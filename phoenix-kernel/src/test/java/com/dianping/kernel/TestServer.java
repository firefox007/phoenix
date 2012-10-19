package com.dianping.kernel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.servlet.GzipFilter;

import com.site.test.jetty.JettyServer;

@RunWith(JUnit4.class)
public class TestServer extends JettyServer {
   public static void main(String[] args) throws Exception {
      TestServer server = new TestServer();

      server.startServer();
      server.showReport();
      server.stopServer();
   }

   @Before
   public void before() throws Exception {
      System.setProperty("devMode", "true");
      super.startServer();
   }

   @Override
   protected String getContextPath() {
      return "/kernel";
   }

   @Override
   protected int getServerPort() {
      return 5376;
   }

   @Override
   protected void postConfigure(WebAppContext context) {
      context.addFilter(GzipFilter.class, "/console/*", Handler.ALL);
   }

   @Test
   public void showReport() throws Exception {
      // open the page in the default browser
      display("/kernel/console");
      waitForAnyKey();
   }
}