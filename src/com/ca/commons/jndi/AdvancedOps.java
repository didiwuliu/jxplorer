package com.ca.commons.jndi;

import javax.naming.*;
import javax.naming.directory.*;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.logging.Logger;

/**
 *   The AdvancedOps class extends BasicOps to allow for complex directory
 *   operations such as manipulating entire trees. <p>
 *
 *   It requires initialisation with a Directory Context, through which
 *   all the low level directory calls are passed (basicOps is a wrapper
 *   to jndi).
 *
 *   It contains a number of functions (pop(), push() and inc() )
 *   that may be over-ridden by
 *   classes derived from this that with to track progress.
 *
 */
public class AdvancedOps extends BasicOps
{
//    protected BasicOps dirOp;

    protected NameParser parser;

    private final static Logger log = Logger.getLogger(AdvancedOps.class.getName());

    /**
     *    Initialise the AdvancedOps object with a BasicOps object. <p>
     *    Warning: the basic ops object is used to obtain a Name Parser
     *    for the current context, which is asumed to be homogenous:
     *    AdvancedOps does *not* support tree operations across multiple
     *    name spaces.
     */

    public AdvancedOps(DirContext c)
            throws NamingException
    {
        super(c);
        parser = getBaseNameParser();
    }

    /**
     *    <p>Create a AdvancedOps object with a ConnectionData object. </p>
     *    <p>Warning: the basic ops object is used to obtain a Name Parser
     *    for the current context, which is asumed to be homogenous:
     *    AdvancedOps does *not* support tree operations across multiple
     *    name spaces.</p>
     */

    public AdvancedOps(ConnectionData cData)
            throws NamingException
    {
        super(cData);
        parser = getBaseNameParser();
    }


    /**
     * 	Factory Method to create BasicOps objects, initialised
     * 	with an ldap context created from the connectionData,
     * 	and maintaining a reference to that connectionData.
     *
     * 	@param cData the details of the directory to connect to
     * 	@return an AdvancedOps object (although it must be cast to
     *    this from the BasicOps required by the method sig - is there
     *    a better way of doing this?).
     */

    public static BasicOps getInstance(ConnectionData cData)
            throws NamingException
    {
        AdvancedOps newObject = new AdvancedOps(openContext(cData));
        return newObject;
    }

    /**
     * overload this method for progress tracker.
     */

    public void startOperation(String heading, String operationName)
    {
    }

    /**
     * overload this method for progress tracker.
     */

    public void stopOperation()
    {
    }

    /**
     * overload this method for progress tracker.
     */

    public void pop()
    {
    }

    /**
     * overload this method for progress tracker.  Note that elements
     * is passed to allow determination of the number of objects - but
     * the Enumeration must be returned without being reset, so be carefull
     * when using it...
     */

    public NamingEnumeration push(NamingEnumeration elements)
    {
        return elements;
    }

    /**
     * <p>New version of push is faster; doesn't require inheriting class to create
     * intermediate enumeration.</p>
      * @param elements
     */
    public void push(ArrayList elements)
    {

    }
    /**
     * overload this method for progress tracker.
     */

    public void inc()
    {
    }

    /*
     *
     *    TREE FUNCTIONS
     *
     */

    public void deleteTree(Name nodeDN)        // may be a single node.
            throws NamingException
    {

        try
        {
            if (nodeDN == null)
                throw new NamingException("null DN passed to deleteTree.");

            log.finer("recursively delete Tree " + nodeDN.toString());

            startOperation("Deleting " + nodeDN.toString(), "deleted ");
            recDeleteTree(nodeDN);
        }
        finally
        {
            stopOperation();
        }

    }

    /**
     *   deletes a subtree by recursively deleting sub-sub trees.
     *
     *   @param dn the distinguished name of the sub-tree apex to delete.
     */

    protected void recDeleteTree(Name dn)
            throws NamingException
    {
        log.info("deleting " + dn);

        ArrayList childArray = getChildren(dn);

        push(childArray);         // inform progress tracker that we're going down a level.

        ListIterator children = childArray.listIterator();

        while (children.hasNext())
        {
            recDeleteTree((Name) children.next());
        }
        pop();              // inform progress tracker that we've come up.

        deleteEntry(dn);
        inc();               // inform progress tracker that we've deleted an object.
    }

    /*
     *
     *    MOVE TREE FUNCTIONS
     *
     */

    /**
     *    Moves a DN to a new DN, including all subordinate entries.
     *    (nb it is up to the implementer how this is done; e.g. if it is an
     *     ldap broker, it may choose rename, or copy-and-delete, as appropriate)
     *
     *    @param oldNodeDN the original DN of the sub tree root (may be a single
     *           entry).
     *    @param newNodeDN the new DN of the sub tree to modify the old tree to.
     */

    public void moveTree(Name oldNodeDN, Name newNodeDN)       // may be a single node.
            throws NamingException
    {
        try
        {
            if (oldNodeDN == null)
                throw new NamingException("the original DN passed to moveTree is null.");

            if (newNodeDN == null)
                throw new NamingException("the destination DN passed to moveTree is null.");

            log.finer("recursively move tree from " + oldNodeDN.toString() + " to " + newNodeDN.toString());

            startOperation("Moving " + oldNodeDN.toString(), "moving");
            recMoveTree(oldNodeDN, newNodeDN);
        }
        finally
        {
            stopOperation();
        }
    }

    /**
     *    <p>Moves a tree.  If the new position is a sibling of the current
     *    position a <i>rename</i> is performed, otherwise a new tree must
     *    be created, with all its children, and then the old tree deleted.<p>
     *
     *    <p>If the new tree creation fails during creation, an attempt is made
     *    to delete the new tree, and the operation fails.  If the new tree
     *    creation succeeds, but the old tree deletion fails, the operation
     *    fails, leaving the new tree and the partial old tree in existence.
     *    (This last should be unlikely.)</p>
     *
     *    <p>This move *deletes* the old value of the RDN when the node is
     *    moved.</p>
     *
     *    @param from the root DN of the tree to be moved
     *    @param to the root DN of the new tree position
     */

    protected void recMoveTree(Name from, Name to)   // may be a single node.
            throws NamingException
    {
        if (from.size() == to.size() && from.startsWith(to.getPrefix(to.size() - 1))) // DNs are siblings...
        {
            try
            {
                renameEntry(from, to, true);
            }
            // special purpose hack to get around directories such as openldap that don't support rename of parents.
            catch (javax.naming.ContextNotEmptyException e)
            {
                if (e.getMessage().indexOf("subtree rename not supported") > -1)
                {
                    recCopyAndDeleteTree(from, to);
                }
            }
        }
        else                               // DNs are not siblings; so copy them
        {                                  // from tree, and then delete the original
            recCopyAndDeleteTree(from, to);
        }
    }

    private void recCopyAndDeleteTree(Name from, Name to)
            throws NamingException
    {
        //TE:   does the 'from' DN exist? What if someone gets the DNs around the wrong way?  For example
        //      in JXweb a user can enter the DN of where to move from & to...what if they, by mistake,
        //      make the 'to' field the 'from' field?  The actual real data will be deleted b/c the copy will
        //      fail due to the 'from' DN not existing and this will fall through to recDeletTree(to)!
        if (!exists(from))
            throw new NamingException("The DN that you are trying to move does not exist.");

        try
        {
            recCopyTree(from, to);
        }
        catch (NamingException e)
        {
            recDeleteTree(to);  // Try to clean up
            throw e;            // then rethrow exception
        }

        recDeleteTree(from);
    }

    /*
     *
     *    COPY TREE FUNCTIONS
     *
     */


    /**
     *    Copies a DN representing a subtree to a new subtree, including
     *    copying all subordinate entries.
     *
     *    @param oldNodeDN the original DN of the sub tree root
     *           to be copied (may be a single entry).
     *    @param newNodeDN the target DN for the tree to be moved to.
     */

    public void copyTree(Name oldNodeDN, Name newNodeDN)       // may be a single node.
            throws NamingException
    {
        try
        {
            if (oldNodeDN == null)
                throw new NamingException("the original DN passed to copyTree is null.");

            if (newNodeDN == null)
                throw new NamingException("the destination DN passed to copyTree is null.");

            log.finer("recursively copy tree from " + oldNodeDN.toString() + " to " + newNodeDN.toString());

            startOperation("Copying " + oldNodeDN.toString(), "copying");
            recCopyTree(oldNodeDN, newNodeDN);
        }
        finally
        {
            stopOperation();
        }
    }


    /**
     *    Takes two DNs, and goes through the first, copying each element
     *    from the top down to the new DN.
     *
     *    @param from the ldap Name dn to copy the tree from
     *    @param to the ldap Name dn to copy the tree to
     */

    protected void recCopyTree(Name from, Name to)
            throws NamingException
    {
        copyEntry(from, to);                                    // where the work finally gets done

        inc();

        ArrayList childArray = getChildren(from);

        push(childArray);

        ListIterator children = childArray.listIterator();

        while (children.hasNext())
        {
            Name childDN = (Name)children.next();

            Name destinationDN = (Name)to.clone();
            destinationDN.add(childDN.get(childDN.size()-1));

            recCopyTree(childDN, destinationDN);
        }
        pop();
    }

    /**
     * <p>This searches for all the children of the given named entry, and returns them as an
     * ArrayList.</p>
     * @param base the base entry to search from
     * @return an ArrayList of [Name] objects representing children.
     */
    protected ArrayList getChildren(Name base)
        throws NamingException
    {
        ArrayList children = new ArrayList();
        NamingEnumeration rawList = list(base);

          if (rawList.hasMoreElements())
          {
              while (rawList.hasMoreElements())
              {
                NameClassPair child = (NameClassPair)rawList.next();
//                if (child.isRelative())  not 'isRelative' appears unreliable as of java 1.4

// XXX Because of apparent short comings in jndi (or maybe I'm using it wrong?) it seems impossible to
// tell whether a DN is relative to the search base or not.  This is particularly bad when it comes
// to dealing with aliases...!  So we check its size instead :-/
//
                Name childDN = parser.parse(child.getName());

                if (childDN.size() == 1)
                {
                     childDN = ((Name)base.clone()).add(child.getName());
                }

                children.add(childDN);
              }
          }
        return children;
    }
}