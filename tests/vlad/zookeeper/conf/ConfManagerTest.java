package vlad.zookeeper.conf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import vlad.zookeeper.conf.ConfManager.CONF_DATA_FORMAT;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.ExponentialBackoffRetry;

public class ConfManagerTest {

	private static final String ZK_PATH = "/test-path";
	private CuratorFramework zooClient;

	public ConfManagerTest() {
	}

	@Before
	public void setUp() throws Exception {
		this.zooClient = CuratorFrameworkFactory.builder().namespace("curatordemo").connectString("localhost:2181")
				.retryPolicy(new ExponentialBackoffRetry(1000, 3)).connectionTimeoutMs(30000).build();

		zooClient.start();

		ConfManager confManager = ConfManager.getInstance();
		confManager.setDataFormat(CONF_DATA_FORMAT.JSON);
		confManager.setClient(zooClient);

	}

	@After
	public void tearDown() throws Exception {
		// this.zooClient.delete().forPath("ZK_PATH");
		this.zooClient.close();
	}

	@Test
	public void testGetPropertyAsString() throws Exception {
		ConfManager cm = ConfManager.getInstance();
		cm.setZookeeperPath(ZK_PATH);
		cm.setClient(this.zooClient);

		// set config file with correct value
		this.zooClient.setData().forPath(ZK_PATH, "{\"test.stringKey.existing\":\"value\"}".getBytes());

		Thread.sleep(100);// may take some time to propagate

		// check default return value
		assertEquals(cm.getPropertyAsString("test.stringKey.not-existing", "default value"), "default value");

		// check default return value as null
		assertNull(cm.getPropertyAsString("test.stringKey.not-existing", null));

		// check value on valid key
		assertEquals(cm.getPropertyAsString("test.stringKey.existing", "default value"), "value");

		// == TEST INVALID VALUES ==
		// set config file with correct value

		// set key value as an array
		this.zooClient.setData().forPath(ZK_PATH, "{\"test.stringKey.existing\":[]}".getBytes());
		
		Thread.sleep(100);// may take some time to propagate
		
		assertEquals(cm.getPropertyAsString("test.stringKey.existing", "default value"), "default value");

		// set key value as double
		this.zooClient.setData().forPath(ZK_PATH, "{\"test.stringKey.existing\":45.0}".getBytes());
		assertEquals(cm.getPropertyAsString("test.stringKey.existing", "default value"), "default value");
	}

	// @Test
	public void testGetPropertyAsInteger() {
		fail("Not yet implemented");
	}

	// @Test
	public void testGetPropertyAsList() {
		fail("Not yet implemented");
	}

	// @Test
	public void testAddChangeListener() {
		fail("Not yet implemented");
	}

}
