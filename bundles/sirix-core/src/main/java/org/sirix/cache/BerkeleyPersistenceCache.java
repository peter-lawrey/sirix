/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sirix.cache;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnegative;

import org.sirix.api.PageReadTrx;
import org.sirix.exception.SirixIOException;
import org.sirix.page.interfaces.KeyValuePage;

import com.google.common.collect.ImmutableMap;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

/**
 * Berkeley implementation of a persistent cache. That means that all data is
 * stored in this cache and it is never removed. This is useful e.g. when it
 * comes to transaction logging.
 * 
 * @author Sebastian Graf, University of Konstanz
 * 
 */
public final class BerkeleyPersistenceCache<T extends KeyValuePage<?, ?>>
		extends AbstractPersistenceCache<Long, RecordPageContainer<T>> {

	/**
	 * Flush after defined value.
	 */
	private static final long FLUSH_AFTER = 10_000;

	/**
	 * Name for the database.
	 */
	private static final String NAME = "berkeleyCache";

	/**
	 * Berkeley database.
	 */
	private final Database mDatabase;

	/**
	 * Berkeley Environment for the database.
	 */
	private Environment mEnv;

	/**
	 * Binding for the key, which is the nodepage.
	 */
	private final TupleBinding<Long> mKeyBinding;

	/**
	 * Binding for the value which is a page with related Nodes.
	 */
	private final PageContainerBinding<T> mValueBinding;

	/** Cache entries. */
	private long mEntries;

	/**
	 * Constructor. Building up the berkeley db and setting necessary settings.
	 * 
	 * @param pPageWriteTrx
	 *          page write transaction
	 * @param file
	 *          the place where the berkeley db is stored.
	 * @param revision
	 *          revision number, needed to reconstruct the sliding window in the
	 *          correct way
	 * @param logType
	 *          type of log to append to the path of the log
	 * @param {@link PageReadTrx} instance
	 * @throws SirixIOException
	 *           if a database error occurs
	 */
	public BerkeleyPersistenceCache(final File file,
			final @Nonnegative int revision, final String logType,
			final PageReadTrx pageReadTrx) throws SirixIOException {
		super(file, revision, logType);
		try {
			// Create a new, transactional database environment.
			final EnvironmentConfig config = new EnvironmentConfig();
			config.setAllowCreate(true).setLocking(false).setCacheSize(1024 * 1024);
			mEnv = new Environment(mPlace, config);

			// Make a database within that environment.
			final DatabaseConfig dbConfig = new DatabaseConfig();
			dbConfig.setAllowCreate(true).setExclusiveCreate(false)
					.setDeferredWrite(true);

			if (removeExistingDatabase(NAME, mEnv)) {
				mEnv = new Environment(mPlace, config);
			}
			mDatabase = mEnv.openDatabase(null, NAME, dbConfig);

			mKeyBinding = TupleBinding.getPrimitiveBinding(Long.class);
			mValueBinding = new PageContainerBinding<>(pageReadTrx);
			mEntries = 0;
		} catch (final DatabaseException e) {
			throw new SirixIOException(e.getCause());
		}
	}

	@Override
	public void putPersistent(final Long key,
			final RecordPageContainer<T> page) throws SirixIOException {
		final DatabaseEntry valueEntry = new DatabaseEntry();
		final DatabaseEntry keyEntry = new DatabaseEntry();
		mEntries++;
		mKeyBinding.objectToEntry(checkNotNull(key), keyEntry);
		mValueBinding.objectToEntry(checkNotNull(page), valueEntry);
		try {
			mDatabase.put(null, keyEntry, valueEntry);
		} catch (final DatabaseException e) {
			throw new SirixIOException(e);
		}

		if (mEntries % FLUSH_AFTER == 0) {
			mDatabase.sync();
		}
	}

	@Override
	public void close() {
		mDatabase.close();
		mEnv.close();
	}

	@Override
	public void clearPersistent() throws SirixIOException {
		final Cursor cursor = mDatabase.openCursor(null, null);
		final DatabaseEntry valueEntry = new DatabaseEntry();
		final DatabaseEntry keyEntry = new DatabaseEntry();
		valueEntry.setPartial(0, 0, true);
		OperationStatus status = cursor.getNext(keyEntry, valueEntry,
				LockMode.DEFAULT);
		try {
			while (status == OperationStatus.SUCCESS) {
				mDatabase.delete(null, keyEntry);
				status = cursor.getNext(keyEntry, valueEntry, LockMode.DEFAULT);
			}
			cursor.close();
		} catch (final DatabaseException e) {
			throw new SirixIOException(e);
		}
	}

	@Override
	public RecordPageContainer<T> getPersistent(final Long key)
			throws SirixIOException {
		final DatabaseEntry valueEntry = new DatabaseEntry();
		final DatabaseEntry keyEntry = new DatabaseEntry();
		mKeyBinding.objectToEntry(checkNotNull(key), keyEntry);
		try {
			final OperationStatus status = mDatabase.get(null, keyEntry, valueEntry,
					LockMode.DEFAULT);
			final RecordPageContainer<T> val = (status == OperationStatus.SUCCESS ? mValueBinding
					.entryToObject(valueEntry) : null);
			return val;
		} catch (final DatabaseException e) {
			throw new SirixIOException(e);
		}
	}

	@Override
	public ImmutableMap<Long, RecordPageContainer<T>> getAll(
			final Iterable<? extends Long> keys) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(
			final Map<? extends Long, ? extends RecordPageContainer<T>> map) {
		for (final Entry<? extends Long, ? extends RecordPageContainer<T>> entry : map
				.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void toSecondCache() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void remove(final Long key) {
		final DatabaseEntry keyEntry = new DatabaseEntry();
		mKeyBinding.objectToEntry(checkNotNull(key), keyEntry);
		final OperationStatus status = mDatabase.delete(null, keyEntry);
		if (status == OperationStatus.NOTFOUND) {
			throw new IllegalStateException();
		}
	}
}
