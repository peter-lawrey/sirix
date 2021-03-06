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
/**
 * 
 */
package org.sirix.node.delegates;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

import org.brackit.xquery.atomic.QNm;
import org.sirix.api.visitor.VisitResultType;
import org.sirix.api.visitor.Visitor;
import org.sirix.node.AbstractForwardingNode;
import org.sirix.node.Kind;
import org.sirix.node.interfaces.NameNode;

import com.google.common.base.Objects;

/**
 * Delegate method for all nodes containing naming-data. That means that
 * different fixed defined names are represented by the nodes delegating the
 * calls of the interface {@link NameNode} to this class. Mainly, keys are
 * stored referencing later on to the string stored in dedicated pages.
 * 
 * @author Sebastian Graf, University of Konstanz
 * @author Johannes Lichtenberger
 * 
 */
public class NameNodeDelegate extends AbstractForwardingNode implements
		NameNode {

	/** Node delegate, containing basic node information. */
	private final NodeDelegate mDelegate;

	/** Key of the prefix. */
	private int mPrefixKey;

	/** Key of the local name */
	private int mLocalNameKey;

	/** URI of the related namespace. */
	private int mUriKey;

	/** Path node key. */
	private long mPathNodeKey;
	
	/**
	 * Constructor.
	 * 
	 * @param delegate
	 *          page delegator
	 * @param uriKey
	 *          uriKey to be stored
	 * @param prefixKey
	 *          prefixKey to be stored
	 * @param localNameKey
	 *          localNameKey to be stored
	 * @param pathNodeKey
	 *          path node key associated with node
	 */
	public NameNodeDelegate(final NodeDelegate delegate,
			final int uriKey, final int prefixKey, final int localNameKey,
			final @Nonnegative long pathNodeKey) {
		assert delegate != null : "delegate must not be null!";
		mDelegate = delegate;
		mPrefixKey = prefixKey;
		mLocalNameKey = localNameKey;
		mUriKey = uriKey;
		assert pathNodeKey >= 0 : "pathNodeKey may not be < 0!";
		mPathNodeKey = pathNodeKey;
	}

	/**
	 * Copy constructor.
	 * 
	 * @param nameDel
	 *          old name node delegate
	 */
	public NameNodeDelegate(final NameNodeDelegate nameDel) {
		mDelegate = nameDel.mDelegate;
		mPrefixKey = nameDel.mPrefixKey;
		mLocalNameKey = nameDel.mLocalNameKey;
		mUriKey = nameDel.mUriKey;
		mPathNodeKey = nameDel.mPathNodeKey;
	}

	@Override
	public Kind getKind() {
		return Kind.UNKNOWN;
	}

	@Override
	public VisitResultType acceptVisitor(final Visitor visitor) {
		return mDelegate.acceptVisitor(visitor);
	}

	@Override
	public int getLocalNameKey() {
		return mLocalNameKey;
	}

	@Override
	public int getURIKey() {
		return mUriKey;
	}

	@Override
	public void setLocalNameKey(final int localNameKey) {
		mLocalNameKey = localNameKey;
	}

	@Override
	public void setURIKey(final int uriKey) {
		mUriKey = uriKey;
	}

	/**
	 * Setting the class path record.
	 * 
	 * @param pPCR
	 *          the path class record to set
	 */
	public void setPathNodeKey(@Nonnegative final long pathNodeKey) {
		assert pathNodeKey >= 0 : "pathNodeKey may not be < 0!";
		mPathNodeKey = pathNodeKey;
	}

	/**
	 * Get the path class record.
	 * 
	 * @return path class record the node belongs to
	 */
	public long getPathNodeKey() {
		return mPathNodeKey;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(mUriKey, mPrefixKey, mLocalNameKey);
	}

	@Override
	public boolean equals(final @Nullable Object obj) {
		if (obj instanceof NameNodeDelegate) {
			final NameNodeDelegate other = (NameNodeDelegate) obj;
			return Objects.equal(mUriKey, other.mUriKey)
					&& Objects.equal(mPrefixKey, other.mPrefixKey)
					&& Objects.equal(mLocalNameKey, other.mLocalNameKey);
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this).add("node delegate", mDelegate)
				.add("uriKey", mUriKey).add("prefixKey", mPrefixKey)
				.add("localNameKey", mLocalNameKey).add("pathNodeKey", mPathNodeKey)
				.toString();
	}

	@Override
	protected NodeDelegate delegate() {
		return mDelegate;
	}

	@Override
	public long getRevision() {
		return mDelegate.getRevision();
	}

	@Override
	public int getPrefixKey() {
		return mPrefixKey;
	}

	@Override
	public void setPrefixKey(int prefixKey) {
		mPrefixKey = prefixKey;
	}

	// Override in concrete node-classes, as the type is not known over here.
	@Override
	public QNm getName() {
		return null;
	}
}
