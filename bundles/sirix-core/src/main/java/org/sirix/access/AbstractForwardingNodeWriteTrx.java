package org.sirix.access;

import javax.annotation.Nonnull;
import javax.xml.stream.XMLEventReader;

import org.brackit.xquery.atomic.QNm;
import org.sirix.api.NodeReadTrx;
import org.sirix.api.NodeWriteTrx;
import org.sirix.exception.SirixException;

/**
 * Forwards all methods to the delegate.
 * 
 * @author Johannes Lichtenberger, University of Konstanz
 * 
 */
public abstract class AbstractForwardingNodeWriteTrx extends
		AbstractForwardingNodeReadTrx implements NodeWriteTrx {

	/** Constructor for use by subclasses. */
	protected AbstractForwardingNodeWriteTrx() {
	}

	@Override
	protected abstract NodeWriteTrx delegate();

	@Override
	public void abort() throws SirixException {
		delegate().abort();
	}

	@Override
	public void close() throws SirixException {
		delegate().close();
	}

	@Override
	public void commit() throws SirixException {
		delegate().commit();
	}

	@Override
	public NodeWriteTrx moveSubtreeToLeftSibling(long fromKey)
			throws SirixException {
		return delegate().moveSubtreeToLeftSibling(fromKey);
	}

	@Override
	public NodeWriteTrx moveSubtreeToRightSibling(long fromKey)
			throws SirixException {
		return delegate().moveSubtreeToRightSibling(fromKey);
	}

	@Override
	public NodeWriteTrx moveSubtreeToFirstChild(long fromKey)
			throws SirixException {
		return delegate().moveSubtreeToFirstChild(fromKey);
	}

	@Override
	public NodeWriteTrx copySubtreeAsFirstChild(NodeReadTrx rtx)
			throws SirixException {
		return delegate().copySubtreeAsFirstChild(rtx);
	}

	@Override
	public NodeWriteTrx copySubtreeAsLeftSibling(NodeReadTrx rtx)
			throws SirixException {
		return delegate().copySubtreeAsLeftSibling(rtx);
	}

	@Override
	public NodeWriteTrx copySubtreeAsRightSibling(NodeReadTrx rtx)
			throws SirixException {
		return delegate().copySubtreeAsRightSibling(rtx);
	}

	@Override
	public NodeWriteTrx insertAttribute(QNm name, @Nonnull String value)
			throws SirixException {
		return delegate().insertAttribute(name, value);
	}

	@Override
	public NodeWriteTrx insertAttribute(QNm name, @Nonnull String value,
			@Nonnull Movement move) throws SirixException {
		return delegate().insertAttribute(name, value, move);
	}

	@Override
	public NodeWriteTrx insertElementAsFirstChild(QNm name)
			throws SirixException {
		return delegate().insertElementAsFirstChild(name);
	}

	@Override
	public NodeWriteTrx insertElementAsLeftSibling(QNm name)
			throws SirixException {
		return delegate().insertElementAsLeftSibling(name);
	}

	@Override
	public NodeWriteTrx insertElementAsRightSibling(QNm name)
			throws SirixException {
		return delegate().insertElementAsRightSibling(name);
	}

	@Override
	public NodeWriteTrx insertNamespace(QNm name) throws SirixException {
		return delegate().insertNamespace(name);
	}

	@Override
	public NodeWriteTrx insertNamespace(QNm name, @Nonnull Movement move)
			throws SirixException {
		return delegate().insertNamespace(name, move);
	}

	@Override
	public NodeWriteTrx insertSubtreeAsFirstChild(XMLEventReader reader)
			throws SirixException {
		return delegate().insertSubtreeAsFirstChild(reader);
	}

	@Override
	public NodeWriteTrx insertSubtreeAsRightSibling(XMLEventReader reader)
			throws SirixException {
		return delegate().insertSubtreeAsRightSibling(reader);
	}

	@Override
	public NodeWriteTrx insertSubtreeAsLeftSibling(XMLEventReader reader)
			throws SirixException {
		return delegate().insertSubtreeAsLeftSibling(reader);
	}

	@Override
	public NodeWriteTrx insertTextAsFirstChild(String value)
			throws SirixException {
		return delegate().insertTextAsFirstChild(value);
	}

	@Override
	public NodeWriteTrx insertTextAsLeftSibling(String value)
			throws SirixException {
		return delegate().insertTextAsLeftSibling(value);
	}

	@Override
	public NodeWriteTrx insertTextAsRightSibling(String value)
			throws SirixException {
		return delegate().insertTextAsRightSibling(value);
	}

	@Override
	public NodeWriteTrx setName(QNm name) throws SirixException {
		return delegate().setName(name);
	}

	@Override
	public NodeWriteTrx setValue(String value) throws SirixException {
		return delegate().setValue(value);
	}

	@Override
	public NodeWriteTrx remove() throws SirixException {
		return delegate().remove();
	}
}
