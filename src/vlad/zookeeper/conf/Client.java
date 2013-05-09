package vlad.zookeeper.conf;

import java.util.ArrayList;
import java.util.Arrays;

import vlad.zookeeper.conf.ConfManager.CONF_DATA_FORMAT;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.ExponentialBackoffRetry;

public class Client {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String zooServers = System.getenv("ZOO_SERVERS");

		if (zooServers == null) {
			zooServers = "127.0.0.2:2181";
		}
		System.out.println(zooServers);

		CuratorFramework zooClient = CuratorFrameworkFactory.builder().namespace("curatordemo")
				.connectString("localhost:2181").retryPolicy(new ExponentialBackoffRetry(1000, 3))
				.connectionTimeoutMs(30000).build();

		zooClient.start();

		ConfManager confManager = ConfManager.getInstance();
		confManager.setDataFormat(CONF_DATA_FORMAT.JSON);
		confManager.setClient(zooClient);

		String serverListProp = confManager.getPropertyAsString("serverList", "defValue");
		Integer changeTestValueProp = confManager.getPropertyAsInteger("changeTestValue", 10);
		Integer maxThreadsProp = confManager.getPropertyAsInteger("maxThreads", 40);
		ArrayList<String> arrayListProp = confManager.getPropertyAsList("testArray", Arrays.asList(""));
		ArrayList<String> nullListProp = confManager.getPropertyAsList("nullArray", Arrays.asList(""));
		ArrayList<Integer> intListProp = confManager.getPropertyAsList("intArray", Arrays.asList(0));

		System.out.println("ServerList: " + serverListProp);
		System.out.println("maxThreads: " + maxThreadsProp);
		System.out.println("ServerList: " + arrayListProp);
		System.out.println("nullList:   " + nullListProp);
		System.out.println("intList:    " + intListProp);
		System.out.println("changeTest: " + changeTestValueProp);

		confManager.addChangeListener("changeTestValue2", new ConfManager.PropertyChangeHandler<Integer>() {
			@Override
			public void handle(Integer newValue) {
				System.out.println("Received new value: " + newValue);
			}
		});

		Thread.sleep(60000);
	}

}
