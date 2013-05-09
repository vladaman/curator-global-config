# Globally Centralized Configuration (Java)

Build on top of Zookeeper using Curator Framework https://github.com/Netflix/curator

### General Overview

Central configuration repository client using Zookeeper. 

* Java Application connects to Zookeeper server using Curator Framework
* Fetches recent configuration settings (property file) and maintains local copy in HashMap for quick lookups
* Client watches znode for changes and updates local HashMap when configuration changes
* Optionally client may add listener on each configuration key to perform actions

### Example

	CuratorFramework zooClient = CuratorFrameworkFactory.builder().namespace("curatordemo")
				.connectString("localhost:2181").retryPolicy(new ExponentialBackoffRetry(1000, 3))
				.connectionTimeoutMs(30000).build();

	zooClient.start();

	ConfManager confManager = ConfManager.getInstance();
	confManager.setDataFormat(CONF_DATA_FORMAT.JSON);
	confManager.setClient(zooClient);