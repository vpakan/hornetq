/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.util;

import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.GroupRequest;
import org.jgroups.util.RspList;
import org.jgroups.util.Rsp;
import org.jgroups.Address;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.ArrayList;

/**
 * A distributed method call addressed to RpcDispatchers configured with RpcServers. It is a simple
 * extension of the MethodCall that offers built-in support for server category. It also offers
 * convenience methods to return results in a normalized format.
 * 
 * @see org.jboss.messaging.util.RpcServer
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class RpcServerCall extends MethodCall
{
   protected Serializable serverCategory;

   /**
    * Must be used by externalization only.
    */
   public RpcServerCall()
   {
      super();
   }

   /**
    * @param serverCategory - the server category. Will be used by the remote RpcServer to select
    *        a set of equivalent sub-servers.
    * @param methodName - the remote method name.
    * @param args - the remote method's arguments.
    * @param types - the argument types (fully qualified class names).
    */
   public RpcServerCall(Serializable serverCategory, String methodName,
                        Object[] args, String[] types)
   {
      super("invoke",
            new Object[] {serverCategory,
                          methodName,
                          args,
                          types},
            new String[] {"java.io.Serializable",
                          "java.lang.String",
                          "[Ljava.lang.Object;",
                          "[Ljava.lang.String;"});
      this.serverCategory = serverCategory;
   }

   public Serializable getServerCategory()
   {
      return serverCategory;
   }

   /**
    * Synchronously invokes the RpcServerCall on all sub-server objects registered under the
    * RPCServerCall's current category, across all RpcServers of the group.
    *
    * @param dispatcher - the dispatcher to use to sent the call from.
    * @param timeout
    *
    * @return a Collection of ServerResponses.
    */

   public Collection remoteInvoke(RpcDispatcher dispatcher, long timeout)
   {
      // TODO for the time being, I am doing synchronous for all
      Vector dests = null;
      int mode = GroupRequest.GET_ALL;
      Collection results = new ArrayList();

      RspList rspList = dispatcher.callRemoteMethods(dests, this, mode, timeout);

      for(int i = 0; i < rspList.size(); i++)
      {
         try
         {
            Rsp response = (Rsp)rspList.elementAt(i);

            // TODO getSender() should be an Address. TO_DO_RSP
            Address address = (Address)response.getSender();

            Object result = response.getValue();
            if (result instanceof Throwable)
            {
               // the remote "invoke" has thrown an exception, the whole category has problems
               results.add(new ServerResponse(address, serverCategory, null, result));
               continue;
            }

            Collection subServerResponses = (Collection)result;
            for(Iterator j = subServerResponses.iterator(); j.hasNext(); )
            {
               SubServerResponse ssr = (SubServerResponse)j.next();
               ServerResponse r = new ServerResponse(address,
                                                     serverCategory,
                                                     ssr.getSubServerID(),
                                                     ssr.getInvocationResult());
               results.add(r);
            }
         }
         catch(Exception e)
         {
            results.add(new ServerResponse(null, serverCategory, null, e));
         }
      }
      return results;
   }
}
