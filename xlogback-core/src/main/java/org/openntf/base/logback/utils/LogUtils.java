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
package org.openntf.base.logback.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;
import java.util.Vector;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.html.DefaultThrowableRenderer;
import ch.qos.logback.classic.html.HTMLLayout;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.helpers.Transform;
import lotus.domino.NotesException;

public class LogUtils {

	public static String getStackTrace(Throwable ee) {
		if(ee==null) return "";
		
		try {
			StringWriter sw = new StringWriter();
			ee.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		} catch (Exception e) {
		}

		return "";
	}

	public static Vector<String> getStackTraceVector(Throwable ee) {
		return getStackTraceVector(ee, 0);
	}

	public static Vector<String> getStackTraceVector(Throwable ee, int skip) {
		Vector<String> v = new Vector<String>(32);
		try {
			StringTokenizer st = new StringTokenizer(getStackTrace(ee), "\n");
			int count = 0;
			while (st.hasMoreTokens()) {
				if (skip <= count++) {
					v.addElement(st.nextToken().trim());
				} else {
					st.nextToken();
				}
			}
	
		} catch (Exception e) {
		}
	
		return v;
	}

	public static String getPlatformName() {
		String platform = System.getProperty("dots.mq.name");
		return StrUtils.defaultIfEmpty(platform, "XSP");		
	}

	
	
	public static void injectCustomConverters(PatternLayout layout) {
		layout.getDefaultConverterMap().put("ex", ThrowableProxyConverterEx.class.getName());
		layout.getDefaultConverterMap().put("exception", ThrowableProxyConverterEx.class.getName());
	}
	
	public static String injectNotesException(IThrowableProxy tp, String oTrace) {
		// Paranoid check
		if(tp == null || oTrace == null || (!(tp instanceof ThrowableProxy))) {
			return oTrace;
		}
		
		Throwable cause = ((ThrowableProxy) tp).getThrowable();
		
		// This check is cheaper than having string comparison.		
		if(! (cause instanceof NotesException)) {
			return oTrace;
		}
		
		String searchStr = "NotesException: null";
		
		if(oTrace.contains(searchStr)) {
			String text = ((NotesException) cause).text;
			int id = ((NotesException) cause).id;
			if(id != 0) { 
				text += " ("+id+")";
			}
			
			String result = searchStr.replace("null", text);
			return oTrace.replaceFirst(searchStr, result);
		}
		
		return oTrace;

	}

	public static void injectCustomThrowableRenderer(HTMLLayout layout) {
		layout.setThrowableRenderer(new DefaultThrowableRendererEx());
	}
	
	
	public static class ThrowableProxyConverterEx extends ThrowableProxyConverter {

		@Override
		protected String throwableProxyToString(IThrowableProxy tp) {
			return LogUtils.injectNotesException(tp, super.throwableProxyToString(tp));
		}

	}

	/**
	 * This class is extended from the original Logback implementation to add NotesException details. 
	 * 
	 * @author Serdar
	 *
	 */
	public static class DefaultThrowableRendererEx extends DefaultThrowableRenderer {

		@Override
		public void printFirstLine(StringBuilder sb, IThrowableProxy tp) {
			String message;
			
			if(tp == null || (!(tp instanceof ThrowableProxy))) {
				super.printFirstLine(sb, tp);
			}

			Throwable cause = ((ThrowableProxy) tp).getThrowable();
			
			if(cause instanceof NotesException) {
				message = ((NotesException) cause).text;
				int id = ((NotesException) cause).id;
				if(id != 0) { 
					message += " ("+id+")";
				}
			} else {
				message = tp.getMessage();
			}
			
			int commonFrames = tp.getCommonFrames();
		    if (commonFrames > 0) {
		      sb.append("<br />").append(CoreConstants.CAUSED_BY);
		    }
		    sb.append(tp.getClassName()).append(": ").append(Transform.escapeTags(message));
		    sb.append(CoreConstants.LINE_SEPARATOR);
		}
		
	}
	
}
