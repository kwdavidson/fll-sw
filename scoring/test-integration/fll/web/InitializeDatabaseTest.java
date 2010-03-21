package fll.web;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import com.thoughtworks.selenium.SeleneseTestCase;

/**
 * Test initializing the database via the web.
 */
public class InitializeDatabaseTest extends SeleneseTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp("http://localhost:9080/setup");
  }

  @Test
  public void testInitializeDatabase() throws IOException {
    selenium.open("http://localhost:9080/fll-sw/setup/");

    final InputStream challengeStream = getClass().getResourceAsStream("data/challenge-ft.xml");
    IntegrationTestUtils.initializeDatabase(selenium, challengeStream, true);
  }
}
