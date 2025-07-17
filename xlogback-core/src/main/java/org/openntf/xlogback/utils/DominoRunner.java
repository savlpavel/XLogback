/*
 * © Copyright Serdar Basegmez. 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.openntf.xlogback.utils;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.NotesThread;
import lotus.domino.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Original implementation taken from XSnippets site.
 * <p>
 * Author: Serdar Basegmez Link: http://openntf.org/s/dominorunner-provides-a-temporary-notes-session-for-your-java-code...
 *
 * @author sbasegmez
 */

public class DominoRunner {

    private static final Logger logger = LoggerFactory.getLogger(DominoRunner.class);

    public interface SessionRoutine<T> {

        T doRun(Session session);

        T fallback();

        T onException(Throwable t);
    }

    public static <T> T runWithSession(boolean trusted, SessionRoutine<T> routine) {
        // We need a session. So first, we'll try to get the session from the NotesContext.
        // If the caller eventually binded to an XPages session, we'll be able to grab a session.
        // The only problem is that; session coming from NotesContext will be the SignerSession.
        // Hope there is only one almighty developer :)

        Session session;

        session = findNotesContextSession(trusted);

        if (null != session) {
            try {
                return routine.doRun(session);
            } catch (Throwable t) {
                return routine.onException(t);
            }
        }

        // So we couldn't grab the session... It means that we are out of XPages.
        // If this is a servlet, we still have a chance for getting a User Session.

        if (!trusted) {
            session = findContextInfoSession();

            if (null != session) {
                logger.trace("Got a NotesContext session!");
                try {
                    return routine.doRun(session);
                } catch (Throwable t) {
                    return routine.onException(t);
                }
            }
        }

        // We might be on an OSGi-level thread, DOTS or a servlet.
        // Either way, we hope we are allowed to have NotesThread session.

        try {
            NotesThread.sinitThread();
            session = NotesFactory.createTrustedSession();

            if (null != session) {
                try {
                    return routine.doRun(session);
                } catch (Throwable t) {
                    return routine.onException(t);
                }
            }
        } catch (NotesException e) {
            // Ooops. We can't have a Session. That means trouble.
            logger.trace("Unable to receive a session from NotesThread: {}", e.text);
        } finally {
            NotesThread.stermThread();
            Utils.recycleObject(session);
        }

        logger.trace("No other option for now. We can't have a Session, we can't load configuration!");

        return routine.fallback();
    }

    public static Session findNotesContextSession(final boolean signer) {
        return AccessController.doPrivileged((PrivilegedAction<Session>) () -> {
            try {
                // This classloader is not available in Wink.
                // Wink servlets replaces classloaders during runtime.
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Class<?> clazz = cl.loadClass("com.ibm.domino.xsp.module.nsf.NotesContext");
                Method m1 = clazz.getDeclaredMethod("getCurrentUnchecked");
                Method m2;
                if (signer) {
                    m2 = clazz.getDeclaredMethod("getSessionAsSignerFullAdmin");
                } else {
                    m2 = clazz.getDeclaredMethod("getCurrentSession");
                }

                Object nc = m1.invoke(null);

                if (nc == null) {
                    // NotesContext is unavailable. We are out of XSP context.
                    return null;
                } else {
                    return (Session) m2.invoke(nc, new Object[0]);
                }
            } catch (ClassNotFoundException e) {
                logger.trace("NotesContext class not found", e);
                // We couldn't find the class.
                return null;
            } catch (NoClassDefFoundError e) {
                logger.trace("NotesContext throwed an exception", e);
                // We couldn't access the class.
                return null;
            } catch (Exception e) {
                logger.trace("Unhandled error looking for the NotesContext session", e);
                return null;
            }
        });
    }

    public static Session findContextInfoSession() {
        return AccessController.doPrivileged((PrivilegedAction<Session>) () -> {
            try {
                // This classloader is not available in Wink.
                // Wink servlets replaces classloaders during runtime.
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Class<?> clazz = cl.loadClass("com.ibm.domino.osgi.core.context.ContextInfo");
                Method m1 = clazz.getDeclaredMethod("getUserSession");

                return (Session) m1.invoke(null, new Object[0]);

            } catch (ClassNotFoundException e) {
                logger.trace("ContextInfo class not found");
                // We couldn't find the class.
            } catch (NoClassDefFoundError e) {
                logger.trace("ContextInfo throwed an exception");
                // We couldn't access the class.
                return null;
            } catch (Throwable t) {
                logger.trace("Unhandled error looking for the ContextInfo session", t);
            }
            return null;
        });
    }

}
