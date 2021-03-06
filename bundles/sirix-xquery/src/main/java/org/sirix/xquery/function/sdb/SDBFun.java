package org.sirix.xquery.function.sdb;

import static org.sirix.xquery.function.sdb.Commit.COMMIT;
import static org.sirix.xquery.function.sdb.Doc.DOC;
import static org.sirix.xquery.function.sdb.GetMostRecentRevision.MOST_RECENT_REVISION;
import static org.sirix.xquery.function.sdb.Load.LOAD;
import static org.sirix.xquery.function.sdb.Store.STORE;
import static org.sirix.xquery.function.sdb.index.FindCASIndex.FIND_CAS_INDEX;
import static org.sirix.xquery.function.sdb.index.FindPathIndex.FIND_PATH_INDEX;
import static org.sirix.xquery.function.sdb.index.create.CreateCASIndex.CREATE_CAS_INDEX;
import static org.sirix.xquery.function.sdb.index.create.CreatePathIndex.CREATE_PATH_INDEX;

import org.brackit.xquery.atomic.QNm;
import org.brackit.xquery.module.Functions;
import org.brackit.xquery.module.Namespaces;
import org.brackit.xquery.xdm.Sequence;
import org.brackit.xquery.xdm.Signature;
import org.brackit.xquery.xdm.type.AtomicType;
import org.brackit.xquery.xdm.type.Cardinality;
import org.brackit.xquery.xdm.type.SequenceType;
import org.sirix.xquery.function.sdb.index.FindCASIndex;
import org.sirix.xquery.function.sdb.index.FindPathIndex;
import org.sirix.xquery.function.sdb.index.create.CreateCASIndex;
import org.sirix.xquery.function.sdb.index.create.CreatePathIndex;
import org.sirix.xquery.function.sdb.index.scan.ScanCASIndex;
import org.sirix.xquery.function.sdb.index.scan.ScanPathIndex;

public final class SDBFun {
	public static final String SDB_PREFIX = "sdb";

	public static final String SDB_NSURI = "https://github.com/sirixdb/sirix";

	public static final QNm ERR_INVALID_ARGUMENT = new QNm(SDB_NSURI, SDB_PREFIX,
			"SIRIXDBF0001");

	public static final QNm ERR_INDEX_NOT_FOUND = new QNm(SDB_NSURI, SDB_PREFIX,
			"SIRIXDBF0002");

	public static final QNm ERR_INVALID_INDEX_TYPE = null;

	public static void register() {
		// dummy function to cause static block
		// to be executed exactly once
	}

	static {
		Namespaces.predefine(SDBFun.SDB_PREFIX, SDBFun.SDB_NSURI);

		// store
		Functions.predefine(new Store(false));
		Functions.predefine(new Store(true));
		Functions.predefine(new Store(STORE, false));
		Functions.predefine(new Store(STORE, true));

		// load
		Functions.predefine(new Load(false));
		Functions.predefine(new Load(true));
		Functions.predefine(new Load(LOAD, false));
		Functions.predefine(new Load(LOAD, true));

		// doc
		Functions.predefine(new Doc(DOC, new Signature(SequenceType.NODE,
				new SequenceType(AtomicType.STR, Cardinality.One), new SequenceType(
						AtomicType.STR, Cardinality.One), new SequenceType(AtomicType.INT,
						Cardinality.ZeroOrOne))));
		Functions.predefine(new Doc(DOC, new Signature(SequenceType.NODE,
				new SequenceType(AtomicType.STR, Cardinality.One), new SequenceType(
						AtomicType.STR, Cardinality.One))));

		// find-path-index
		Functions.predefine(new FindPathIndex(FIND_PATH_INDEX, new Signature(
				SequenceType.INTEGER, SequenceType.NODE, SequenceType.STRING)));

		// find-cas-indec
		Functions.predefine(new FindCASIndex(FIND_CAS_INDEX, new Signature(
				SequenceType.INTEGER, SequenceType.NODE, SequenceType.STRING)));

		// commit
		Functions.predefine(new Commit(COMMIT, new Signature(SequenceType.INTEGER,
				SequenceType.NODE)));

		// get-most-recent-rev
		Functions.predefine(new GetMostRecentRevision(MOST_RECENT_REVISION,
				new Signature(SequenceType.INTEGER, SequenceType.NODE)));

		// create-path-index
		Functions.predefine(new CreatePathIndex(CREATE_PATH_INDEX, new Signature(
				SequenceType.NODE, SequenceType.NODE, new SequenceType(AtomicType.STR,
						Cardinality.ZeroOrMany))));
		Functions.predefine(new CreatePathIndex(CREATE_PATH_INDEX, new Signature(
				SequenceType.NODE, SequenceType.NODE)));

		// create-cas-index
		Functions.predefine(new CreateCASIndex(CREATE_CAS_INDEX, new Signature(
				SequenceType.NODE, SequenceType.NODE, new SequenceType(AtomicType.STR,
						Cardinality.ZeroOrOne), new SequenceType(AtomicType.STR,
						Cardinality.ZeroOrMany))));
		Functions.predefine(new CreateCASIndex(CREATE_CAS_INDEX, new Signature(
				SequenceType.NODE, SequenceType.NODE, new SequenceType(AtomicType.STR,
						Cardinality.ZeroOrOne))));
		Functions.predefine(new CreateCASIndex(CREATE_CAS_INDEX, new Signature(
				SequenceType.NODE, SequenceType.NODE)));

		Functions.predefine(new ScanPathIndex());
		Functions.predefine(new ScanCASIndex());
	}
}
