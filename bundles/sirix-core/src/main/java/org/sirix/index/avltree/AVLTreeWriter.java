package org.sirix.index.avltree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

import org.sirix.access.AbstractForwardingNodeCursor;
import org.sirix.api.NodeCursor;
import org.sirix.api.PageWriteTrx;
import org.sirix.cache.IndirectPageLogKey;
import org.sirix.exception.SirixIOException;
import org.sirix.index.IndexType;
import org.sirix.index.SearchMode;
import org.sirix.index.avltree.AVLTreeReader.MoveCursor;
import org.sirix.index.avltree.interfaces.References;
import org.sirix.node.DocumentRootNode;
import org.sirix.node.SirixDeweyID;
import org.sirix.node.delegates.NodeDelegate;
import org.sirix.node.interfaces.Node;
import org.sirix.node.interfaces.Record;
import org.sirix.page.CASPage;
import org.sirix.page.NamePage;
import org.sirix.page.PageKind;
import org.sirix.page.PathPage;
import org.sirix.page.RevisionRootPage;
import org.sirix.page.UnorderedKeyValuePage;
import org.sirix.settings.Fixed;
import org.sirix.utils.LogWrapper;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 * Simple AVLTreeWriter (balanced binary search-tree -- based on BaseX(.org)
 * version).
 * 
 * @author Johannes Lichtenberger, University of Konstanz
 * 
 * @param <K>
 *          the key to search for or insert
 * @param <V>
 *          the value
 */
public final class AVLTreeWriter<K extends Comparable<? super K>, V extends References>
		extends AbstractForwardingNodeCursor {
	/** Logger. */
	private static final LogWrapper LOGGER = new LogWrapper(
			LoggerFactory.getLogger(AVLTreeWriter.class));

	/** {@link AVLTreeReader} instance. */
	private final AVLTreeReader<K, V> mAVLTreeReader;

	/** {@link PageWriteTrx} instance. */
	private final PageWriteTrx<Long, Record, UnorderedKeyValuePage> mPageWriteTrx;

	/**
	 * Private constructor.
	 * 
	 * @param pageWriteTrx
	 *          {@link PageWriteTrx} for persistent storage
	 * @param type
	 *          type of index
	 */
	private AVLTreeWriter(
			final PageWriteTrx<Long, Record, UnorderedKeyValuePage> pageWriteTrx,
			final IndexType type, final @Nonnegative int index) {
		try {
			switch (type) {
			case PATH:
				// Create path index tree if needed.
				final PathPage pathPage = pageWriteTrx.getPathPage(pageWriteTrx
						.getActualRevisionRootPage());
				pageWriteTrx.putPageIntoCache(new IndirectPageLogKey(PageKind.PATHPAGE,
						-1, -1, 0), pathPage);
				pathPage.createPathIndexTree(pageWriteTrx, index);
				break;
			case CAS:
				// Create CAS index tree if needed.
				final CASPage casPage = pageWriteTrx.getCASPage(pageWriteTrx
						.getActualRevisionRootPage());
				pageWriteTrx.putPageIntoCache(new IndirectPageLogKey(PageKind.PATHPAGE,
						-1, -1, 0), casPage);
				casPage.createCASIndexTree(pageWriteTrx, index);
				break;
			case NAME:
				// Create name index tree if needed.
				final NamePage namePage = pageWriteTrx.getNamePage(pageWriteTrx
						.getActualRevisionRootPage());
				pageWriteTrx.putPageIntoCache(new IndirectPageLogKey(PageKind.PATHPAGE,
						-1, -1, 0), namePage);
				namePage.createNameIndexTree(pageWriteTrx, index);
				break;
			}
		} catch (final SirixIOException e) {
			LOGGER.error(e.getMessage(), e);
		}
		mAVLTreeReader = AVLTreeReader.getInstance(pageWriteTrx, type, index);
		mPageWriteTrx = pageWriteTrx;
	}

	/**
	 * Get a new instance.
	 * 
	 * @param pageWriteTrx
	 *          {@link PageWriteTrx} for persistent storage
	 * @param type
	 *          type of index
	 * @param index
	 *          the index number
	 * @return new tree instance
	 */
	public static <K extends Comparable<? super K>, V extends References> AVLTreeWriter<K, V> getInstance(
			final PageWriteTrx<Long, Record, UnorderedKeyValuePage> pageWriteTrx,
			final IndexType type, final int index) {
		return new AVLTreeWriter<K, V>(pageWriteTrx, type, index);
	}

	/**
	 * Checks if the specified token is already indexed; if yes, returns its
	 * reference. Otherwise, creates a new index entry and returns a reference of
	 * the indexed token.
	 * 
	 * @param key
	 *          token to be indexed
	 * @param value
	 *          node key references
	 * @param move
	 *          determines if AVLNode cursor must be moved to document root/root
	 *          node or not
	 * @return indexed node key references
	 * @throws SirixIOException
	 *           if an I/O error occurs
	 */
	@SuppressWarnings("unchecked")
	public V index(final K key, final V value,
			final MoveCursor move) throws SirixIOException {
		if (move == MoveCursor.TO_DOCUMENT_ROOT) {
			moveToDocumentRoot();
		}
		final RevisionRootPage root = mPageWriteTrx.getActualRevisionRootPage();
		if (mAVLTreeReader.getAVLNode() == null
				&& ((DocumentRootNode) getNode()).getFirstChildKey() == Fixed.NULL_NODE_KEY
						.getStandardProperty()) {
			// Index is empty.. create root node.
			final long nodeKey = getNewNodeKey(root);
			final AVLNode<K, V> treeRoot = (AVLNode<K, V>) mPageWriteTrx.createEntry(
					nodeKey,
					new AVLNode<>(key, value, new NodeDelegate(nodeKey,
							Fixed.DOCUMENT_NODE_KEY.getStandardProperty(), 0, 0, Optional
									.<SirixDeweyID> absent())), mAVLTreeReader.mPageKind,
					mAVLTreeReader.mIndex, Optional.<UnorderedKeyValuePage> absent());
			final DocumentRootNode document = (DocumentRootNode) mPageWriteTrx
					.prepareEntryForModification(
							Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			document.setFirstChildKey(treeRoot.getNodeKey());
			document.incrementChildCount();
			document.incrementDescendantCount();
			return value;
		}

		if (move == MoveCursor.TO_DOCUMENT_ROOT
				|| mAVLTreeReader.getAVLNode() == null) {
			moveToDocumentRoot();
			moveToFirstChild();
		}
		AVLNode<K, V> node = mAVLTreeReader.getAVLNode();
		while (true) {
			final int c = key.compareTo(node.getKey());
			if (c == 0) {
				if (!value.equals(node.getValue())) {
					final AVLNode<K, V> avlNode = (AVLNode<K, V>) mPageWriteTrx
							.prepareEntryForModification(node.getNodeKey(),
									mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
									Optional.<UnorderedKeyValuePage> absent());
					avlNode.setValue(value);
				}
				return node.getValue();
			}

			final boolean moved = c < 0 ? moveToFirstChild().hasMoved()
					: moveToLastChild().hasMoved();
			if (moved) {
				node = mAVLTreeReader.getAVLNode();
				continue;
			}

			final long nodeKey = getNewNodeKey(root);
			final AVLNode<K, V> child = (AVLNode<K, V>) mPageWriteTrx.createEntry(
					nodeKey,
					new AVLNode<>(key, value, new NodeDelegate(nodeKey,
							node.getNodeKey(), 0, 0, Optional.<SirixDeweyID> absent())),
					mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex, Optional
							.<UnorderedKeyValuePage> absent());
			node = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
					node.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
					Optional.<UnorderedKeyValuePage> absent());
			if (c < 0) {
				node.setLeftChildKey(child.getNodeKey());
				adjust(child);
			} else {
				node.setRightChildKey(child.getNodeKey());
				adjust(child);
			}
			final DocumentRootNode document = (DocumentRootNode) mPageWriteTrx
					.prepareEntryForModification(
							Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			document.incrementDescendantCount();
			return value;
		}
	}

	/** 
	 * Get the new maximum node key.
	 * 
	 * @param root
	 * 				the current {@link RevisionRootPage}
	 * @return maximum node key
	 * @throws SirixIOException
	 */
	private long getNewNodeKey(final RevisionRootPage root)
			throws SirixIOException {
		switch (mAVLTreeReader.mPageKind) {
		case PATHPAGE:
			return mPageWriteTrx.getPathPage(root).getMaxNodeKey(
					mAVLTreeReader.mIndex) + 1;
		case CASPAGE:
			return mPageWriteTrx.getCASPage(root)
					.getMaxNodeKey(mAVLTreeReader.mIndex) + 1;
		case NAMEPAGE:
			return mPageWriteTrx.getNamePage(root).getMaxNodeKey(
					mAVLTreeReader.mIndex) + 1;
		case PATHSUMMARYPAGE:
			return mPageWriteTrx.getPathSummaryPage(root).getMaxNodeKey(
					mAVLTreeReader.mIndex) + 1;
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Remove a node key from the value, or remove the whole node, if no keys are
	 * stored anymore.
	 * 
	 * @param key
	 *          the key for which to search the value
	 * @param nodeKey
	 *          the nodeKey to remove from the value
	 * @throws SirixIOException
	 *           if an I/O error occured
	 */
	public boolean remove(final K key, final @Nonnegative long nodeKey)
			throws SirixIOException {
		checkArgument(nodeKey >= 0, "nodeKey must be >= 0!");
		final Optional<V> searchedValue = mAVLTreeReader.get(checkNotNull(key),
				SearchMode.EQUAL);
		boolean removed = false;
		if (searchedValue.isPresent()) {
			final V value = searchedValue.get();
			removed = value.removeNodeKey(nodeKey);
			// final boolean removed = value.removeNodeKey(nodeKey);
			//
			// @SuppressWarnings("unchecked")
			// final AVLNode<K, V> node = (AVLNode<K, V>) mAVLTreeReader.mPageWriteTrx
			// .prepareEntryForModification(mAVLTreeReader.getNodeKey(),
			// mAVLTreeReader.mPageKind,
			// Optional.<UnorderedKeyValuePage> absent());
			// node.setValue(value);
			//
			// if (removed) {
			// return;
			// }
			//
			// removeNode();
		}
		return removed;
	}

	/**
	 * Remove a node in the AVLTree.
	 * 
	 * @throws SirixIOException
	 *           if an I/O error occurs
	 */
	@SuppressWarnings("unused")
	private void removeNode() throws SirixIOException {
		AVLNode<K, V> toDelete = mAVLTreeReader.getAVLNode();
		// Case: No children.
		if (!toDelete.hasLeftChild() && !toDelete.hasRightChild()) {
			if (toDelete.getParentKey() == Fixed.DOCUMENT_NODE_KEY
					.getStandardProperty()) {
				// Root node removed.
				final DocumentRootNode document = (DocumentRootNode) mPageWriteTrx
						.prepareEntryForModification(
								Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				document.setFirstChildKey(Fixed.NULL_NODE_KEY.getStandardProperty());
			} else {
				moveToParent();
				if (mAVLTreeReader.getAVLNode().getLeftChildKey() == toDelete
						.getNodeKey()) {
					@SuppressWarnings("unchecked")
					final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
							.prepareEntryForModification(mAVLTreeReader.getAVLNode()
									.getNodeKey(), mAVLTreeReader.mPageKind,
									mAVLTreeReader.mIndex, Optional
											.<UnorderedKeyValuePage> absent());
					parent.setLeftChildKey(Fixed.NULL_NODE_KEY.getStandardProperty());
					parent.setChanged(true);
				} else {
					assert mAVLTreeReader.getAVLNode().getRightChildKey() == toDelete
							.getNodeKey();

					@SuppressWarnings("unchecked")
					final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
							.prepareEntryForModification(mAVLTreeReader.getAVLNode()
									.getNodeKey(), mAVLTreeReader.mPageKind,
									mAVLTreeReader.mIndex, Optional
											.<UnorderedKeyValuePage> absent());
					parent.setRightChildKey(Fixed.NULL_NODE_KEY.getStandardProperty());
					parent.setChanged(true);
				}
				moveTo(toDelete.getNodeKey());

				// Balance up to root.
				adjust(mAVLTreeReader.getAVLNode());
			}

			mPageWriteTrx.removeEntry(toDelete.getNodeKey(),
					mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
					Optional.<UnorderedKeyValuePage> absent());
			return;
		}
		// Case: 1 child.
		if (toDelete.hasLeftChild() && !toDelete.hasRightChild()) {
			if (toDelete.getParentKey() == Fixed.DOCUMENT_NODE_KEY
					.getStandardProperty()) {
				final DocumentRootNode document = (DocumentRootNode) mPageWriteTrx
						.prepareEntryForModification(
								Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				document.setFirstChildKey(toDelete.getLeftChildKey());
			} else {
				@SuppressWarnings("unchecked")
				final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
						.prepareEntryForModification(toDelete.getParentKey(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				parent.setLeftChildKey(toDelete.getLeftChildKey());
				parent.setChanged(true);
			}

			@SuppressWarnings("unchecked")
			final AVLNode<K, V> leftChild = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(toDelete.getLeftChildKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			leftChild.setParentKey(toDelete.getParentKey());

			// Balance up to root.
			adjust(mAVLTreeReader.getAVLNode());

			// Remove deleted node.
			mPageWriteTrx.removeEntry(toDelete.getNodeKey(),
					mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
					Optional.<UnorderedKeyValuePage> absent());
			return;
		}
		// Case: 1 child.
		if (!toDelete.hasLeftChild() && toDelete.hasRightChild()) {
			if (toDelete.getParentKey() == Fixed.DOCUMENT_NODE_KEY
					.getStandardProperty()) {
				final DocumentRootNode document = (DocumentRootNode) mPageWriteTrx
						.prepareEntryForModification(
								Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				document.setFirstChildKey(toDelete.getRightChildKey());
			} else {
				@SuppressWarnings("unchecked")
				final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
						.prepareEntryForModification(toDelete.getParentKey(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				parent.setRightChildKey(toDelete.getRightChildKey());
				parent.setChanged(true);
			}

			@SuppressWarnings("unchecked")
			final AVLNode<K, V> rightChild = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(toDelete.getRightChildKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			rightChild.setParentKey(toDelete.getParentKey());

			// Balance up to root.
			adjust(mAVLTreeReader.getAVLNode());

			// Remove deleted node.
			mPageWriteTrx.removeEntry(toDelete.getNodeKey(),
					mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
					Optional.<UnorderedKeyValuePage> absent());
			return;
		}
		assert toDelete.hasLeftChild() && toDelete.hasRightChild();
		// Case: 2 children.
		// Move to successor.
		moveToLastChild();
		while (mAVLTreeReader.getAVLNode().hasLeftChild()) {
			moveToFirstChild();
		}

		final AVLNode<K, V> successor = mAVLTreeReader.getAVLNode();
		final long nodeKey = successor.getNodeKey();

		@SuppressWarnings("unchecked")
		final AVLNode<K, V> leftChild = (AVLNode<K, V>) mPageWriteTrx
				.prepareEntryForModification(toDelete.getLeftChildKey(),
						mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
						Optional.<UnorderedKeyValuePage> absent());
		leftChild.setParentKey(successor.getNodeKey());

		if (toDelete.getRightChildKey() != successor.getNodeKey()) {
			@SuppressWarnings("unchecked")
			final AVLNode<K, V> rightChild = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(toDelete.getRightChildKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			rightChild.setParentKey(successor.getNodeKey());

			if (successor.hasRightChild()) {
				// The only subtree.
				@SuppressWarnings("unchecked")
				final AVLNode<K, V> child = (AVLNode<K, V>) mPageWriteTrx
						.prepareEntryForModification(successor.getRightChildKey(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				child.setParentKey(successor.getParentKey());

				@SuppressWarnings("unchecked")
				final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
						.prepareEntryForModification(successor.getParentKey(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				parent.setLeftChildKey(successor.getRightChildKey());
				parent.setChanged(true);
			} else {
				@SuppressWarnings("unchecked")
				final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
						.prepareEntryForModification(successor.getParentKey(),
								mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
								Optional.<UnorderedKeyValuePage> absent());
				parent.setLeftChildKey(Fixed.NULL_NODE_KEY.getStandardProperty());
				parent.setChanged(true);
			}
		}

		@SuppressWarnings("unchecked")
		final AVLNode<K, V> successorToModify = (AVLNode<K, V>) mPageWriteTrx
				.prepareEntryForModification(nodeKey, mAVLTreeReader.mPageKind,
						mAVLTreeReader.mIndex, Optional.<UnorderedKeyValuePage> absent());
		assert toDelete.getLeftChildKey() != Fixed.NULL_NODE_KEY
				.getStandardProperty()
				&& toDelete.getRightChildKey() != Fixed.NULL_NODE_KEY
						.getStandardProperty();
		successorToModify.setLeftChildKey(toDelete.getLeftChildKey());
		if (successor.getNodeKey() != toDelete.getRightChildKey()) {
			successorToModify.setRightChildKey(toDelete.getRightChildKey());
		}
		successorToModify.setParentKey(toDelete.getParentKey());

		if (toDelete.getParentKey() == Fixed.DOCUMENT_NODE_KEY
				.getStandardProperty()) {
			final DocumentRootNode parent = (DocumentRootNode) mPageWriteTrx
					.prepareEntryForModification(toDelete.getParentKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setFirstChildKey(successor.getNodeKey());
		} else {
			@SuppressWarnings("unchecked")
			final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(toDelete.getParentKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			if (parent.getLeftChildKey() == toDelete.getNodeKey()) {
				parent.setLeftChildKey(successor.getNodeKey());
			} else {
				assert parent.getRightChildKey() == toDelete.getNodeKey();
				parent.setRightChildKey(successor.getNodeKey());
			}
		}

		// Balance up to root.
		moveTo(nodeKey);
		adjust(mAVLTreeReader.getAVLNode());

		mPageWriteTrx.removeEntry(toDelete.getNodeKey(), mAVLTreeReader.mPageKind,
				mAVLTreeReader.mIndex, Optional.<UnorderedKeyValuePage> absent());
	}

	/**
	 * Adjusts the tree balance.
	 * 
	 * @param node
	 *          node to be adjusted
	 * @throws SirixIOException
	 *           if an I/O error occurs
	 */
	private void adjust(AVLNode<K, V> node) throws SirixIOException {
		setChanged(node, true);

		while (node != null
				&& node.getParentKey() != Fixed.DOCUMENT_NODE_KEY.getStandardProperty()
				&& parent(node) != null && parent(node).isChanged()) {
			if (parent(node).equals(left(parent(parent(node))))) {
				AVLNode<K, V> y = right(parent(parent(node)));
				if (y != null && y.isChanged()) {
					setChanged(parent(node), false);
					y.setChanged(false);
					setChanged(parent(parent(node)), true);
					node = parent(parent(node));
				} else {
					if (node.equals(right(parent(node)))) {
						node = parent(node);
						rotateLeft(node);
					}
					setChanged(parent(node), false);
					setChanged(parent(parent(node)), true);
					if (parent(parent(node)) != null) rotateRight(parent(parent(node)));
				}
			} else if (parent(node).equals(right(parent(parent(node))))) {
				AVLNode<K, V> y = left(parent(parent(node)));
				if (y != null && y.isChanged()) {
					setChanged(parent(node), false);
					setChanged(y, false);
					setChanged(parent(parent(node)), true);
					node = parent(parent(node));
				} else {
					if (node.equals(left(parent(node)))) {
						node = parent(node);
						rotateRight(node);
					}
					setChanged(parent(node), false);
					setChanged(parent(parent(node)), true);
					if (parent(parent(node)) != null) rotateLeft(parent(parent(node)));
				}
			} else {
				node = null;
			}
		}

		final long nodeKey = getNodeKey();
		moveToDocumentRoot();
		if (((DocumentRootNode) getNode()).hasFirstChild()) {
			moveToFirstChild();
			setChanged(mAVLTreeReader.getAVLNode(), false);
		}
		moveTo(nodeKey);
	}

	/**
	 * Set changed value.
	 * 
	 * @param nodeToChange
	 *          node to adjust
	 * @param changed
	 *          changed value
	 * @throws SirixIOException
	 *           if an I/O error occurs
	 */
	private void setChanged(final AVLNode<K, V> nodeToChange,
			final boolean changed) throws SirixIOException {
		@SuppressWarnings("unchecked")
		final AVLNode<K, V> node = (AVLNode<K, V>) mPageWriteTrx
				.prepareEntryForModification(nodeToChange.getNodeKey(),
						mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
						Optional.<UnorderedKeyValuePage> absent());
		node.setChanged(changed);
	}

	/**
	 * Returns the left child node.
	 * 
	 * @param node
	 *          node from which to move to and return the left sibling
	 * @return left child node or {@code null}
	 */
	private AVLNode<K, V> left(@Nullable AVLNode<K, V> node) {
		if (node == null
				|| node.getLeftChildKey() == Fixed.NULL_NODE_KEY.getStandardProperty()) {
			return null;
		}
		return moveTo(node.getLeftChildKey()).hasMoved() ? mAVLTreeReader
				.getAVLNode() : null;
	}

	/**
	 * Returns the right child node.
	 * 
	 * @param node
	 *          node from which to move to and return the right sibling
	 * @return right child node or {@code null}
	 */
	private AVLNode<K, V> right(@Nullable AVLNode<K, V> node) {
		if (node == null
				|| node.getRightChildKey() == Fixed.NULL_NODE_KEY.getStandardProperty()) {
			return null;
		}
		return moveTo(node.getRightChildKey()).hasMoved() ? mAVLTreeReader
				.getAVLNode() : null;
	}

	/**
	 * Returns the parent node.
	 * 
	 * @param node
	 *          node from which to move to and return the parent node
	 * @return parent node or {@code null}
	 */
	private AVLNode<K, V> parent(@Nullable AVLNode<K, V> node) {
		if (node == null
				|| node.getParentKey() == Fixed.NULL_NODE_KEY.getStandardProperty()) {
			return null;
		}
		return moveTo(node.getParentKey()).hasMoved() ? mAVLTreeReader.getAVLNode()
				: null;
	}

	/**
	 * Left rotation.
	 * 
	 * @param node
	 *          node to be rotated
	 * @throws SirixIOException
	 *           if an I/O error occurs
	 */
	@SuppressWarnings({ "unchecked" })
	private void rotateLeft(AVLNode<K, V> node) throws SirixIOException {
		moveTo(node.getNodeKey());

		AVLNode<K, V> right = ((AVLTreeReader<K, V>) moveToLastChild().get())
				.getAVLNode();

		node = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				node.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
				Optional.<UnorderedKeyValuePage> absent());
		node.setRightChildKey(right.getLeftChildKey());

		if (right.hasLeftChild()) {
			final AVLNode<K, V> rightLeftChild = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(right.getLeftChildKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			rightLeftChild.setParentKey(node.getNodeKey());
		}

		right = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				right.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
				Optional.<UnorderedKeyValuePage> absent());
		right.setParentKey(node.getParentKey());

		if (node.getParentKey() == Fixed.DOCUMENT_NODE_KEY.getStandardProperty()) {
			final DocumentRootNode parent = (DocumentRootNode) mPageWriteTrx
					.prepareEntryForModification(
							Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setFirstChildKey(right.getNodeKey());
		} else if (moveTo(node.getParentKey()).hasMoved()
				&& mAVLTreeReader.getAVLNode().getLeftChildKey() == node.getNodeKey()) {
			final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(mAVLTreeReader.getNodeKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setLeftChildKey(right.getNodeKey());
		} else {
			final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(mAVLTreeReader.getNodeKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setRightChildKey(right.getNodeKey());
		}

		right = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				right.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
				Optional.<UnorderedKeyValuePage> absent());
		right.setLeftChildKey(node.getNodeKey());

		node = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				node.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
				Optional.<UnorderedKeyValuePage> absent());
		node.setParentKey(right.getNodeKey());
	}

	/**
	 * Right rotation.
	 * 
	 * @param node
	 *          node to be rotated
	 * @throws SirixIOException
	 *           if an I/O error occurs
	 */
	@SuppressWarnings({ "unchecked" })
	private void rotateRight(AVLNode<K, V> node) throws SirixIOException {
		moveTo(node.getNodeKey());

		AVLNode<K, V> leftChild = ((AVLTreeReader<K, V>) moveToFirstChild().get())
				.getAVLNode();
		node = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				node.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
				Optional.<UnorderedKeyValuePage> absent());
		node.setLeftChildKey(leftChild.getRightChildKey());

		if (leftChild.hasRightChild()) {
			final Node leftRightChild = (Node) mPageWriteTrx
					.prepareEntryForModification(leftChild.getRightChildKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			leftRightChild.setParentKey(node.getNodeKey());
		}

		leftChild = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				leftChild.getNodeKey(), mAVLTreeReader.mPageKind,
				mAVLTreeReader.mIndex, Optional.<UnorderedKeyValuePage> absent());
		leftChild.setParentKey(node.getParentKey());

		if (node.getParentKey() == Fixed.DOCUMENT_NODE_KEY.getStandardProperty()) {
			final DocumentRootNode parent = (DocumentRootNode) mPageWriteTrx
					.prepareEntryForModification(
							Fixed.DOCUMENT_NODE_KEY.getStandardProperty(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setFirstChildKey(leftChild.getNodeKey());
		} else if (moveTo(node.getParentKey()).hasMoved()
				&& mAVLTreeReader.getAVLNode().getRightChildKey() == node.getNodeKey()) {
			final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(mAVLTreeReader.getNodeKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setRightChildKey(leftChild.getNodeKey());
		} else {
			final AVLNode<K, V> parent = (AVLNode<K, V>) mPageWriteTrx
					.prepareEntryForModification(mAVLTreeReader.getNodeKey(),
							mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
							Optional.<UnorderedKeyValuePage> absent());
			parent.setLeftChildKey(leftChild.getNodeKey());
		}

		leftChild = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				leftChild.getNodeKey(), mAVLTreeReader.mPageKind,
				mAVLTreeReader.mIndex, Optional.<UnorderedKeyValuePage> absent());
		leftChild.setRightChildKey(node.getNodeKey());

		node = (AVLNode<K, V>) mPageWriteTrx.prepareEntryForModification(
				node.getNodeKey(), mAVLTreeReader.mPageKind, mAVLTreeReader.mIndex,
				Optional.<UnorderedKeyValuePage> absent());
		node.setParentKey(leftChild.getNodeKey());
	}

	@Override
	public void close() throws Exception {
	}

	@Override
	protected NodeCursor delegate() {
		return mAVLTreeReader;
	}

	/**
	 * Finds the specified key in the index and returns its value.
	 * 
	 * @param key
	 *          key to be found
	 * @param mode
	 *          the search mode
	 * @return {@link Optional} reference (with the found value, or a reference
	 *         which indicates that the value hasn't been found)
	 */
	public Optional<V> get(final K key, final SearchMode mode) {
		return mAVLTreeReader.get(checkNotNull(key), checkNotNull(mode));
	}

	/**
	 * Get the {@link AVLTreeReader} used to navigate.
	 * 
	 * @return {@link AVLTreeReader} reference
	 */
	public AVLTreeReader<K, V> getReader() {
		return mAVLTreeReader;
	}
}
