package net.shrimpworks.zomb.slack;

import java.io.IOException;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import net.jadler.Jadler;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ZombClientTest {

	private static final int jadlerPort = 9080;

	@Before
	public void setup() {
		Jadler.initJadlerListeningOn(jadlerPort);
	}

	@Test
	public void clientTest() throws IOException {
		final String key = "mykey";
		final String user = "bob";
		final String query = "hello lol";

		Jadler.onRequest()
			  .havingMethodEqualTo("POST")
			  .havingPathEqualTo("/")
			  .havingBody(new BaseMatcher<String>() {
				  @Override
				  public boolean matches(Object o) {
					  if (!(o instanceof String)) throw new IllegalArgumentException("o is not a String");

					  JsonObject j = JsonObject.readFrom(o.toString());

					  return j.get("key").asString().equals(key);
				  }

				  @Override
				  public void describeTo(Description description) {
					  // wat
				  }
			  })
			  .respond()
			  .withContentType("application/json")
			  .withStatus(200)
			  .withBody(
					  new JsonObject()
							  .add("plugin", "hello")
							  .add("user", user)
							  .add("query", query)
							  .add("response", new JsonArray()
									  .add("hello"))
							  .add("image", "")
							  .toString()
			  );

		ZombClient c = new ZombClient(String.format("http://localhost:%d/", jadlerPort), key);

		ZombClient.Response response = c.execute(user, query);
		assertNotNull(response);

		assertEquals(user, response.user());
		assertEquals(query, response.query());
		assertEquals("hello", response.plugin());

		assertEquals(1, response.response().length);
		assertEquals("hello", response.response()[0]);
	}

}
