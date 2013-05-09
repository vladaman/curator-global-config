# Globally Centralized Configuration (Java)

Central configuration repository client using Zookeeper.  Build on top of Zookeeper using Curator Framework https://github.com/Netflix/curator

### General Overview

* Java Application connects to Zookeeper server using Curator Framework
* Fetches recent configuration settings (property file) and maintains local copy in HashMap for quick lookups
* Client watches znode for changes and updates local HashMap when configuration changes
* Optionally client may add listener on each configuration key to perform actions

### Example

    // Zookeeper CuratorFramework Connetions
    CuratorFramework zooClient = CuratorFrameworkFactory.builder().namespace("curatordemo")
				.connectString("localhost:2181").retryPolicy(new ExponentialBackoffRetry(1000, 3))
				.connectionTimeoutMs(30000).build();
    zooClient.start();

    // Configuration Manager Initialization
    ConfManager confManager = ConfManager.getInstance();
    confManager.setDataFormat(CONF_DATA_FORMAT.JSON); // assumes data to be in JSON
    confManager.setClient(zooClient);
    
    // Read Properties (String, Integers, Arrays)
    String stringProp = confManager.getPropertyAsString("test.stringKey", "defValue");
    Integer intProp = confManager.getPropertyAsInteger("test.intKey", 10);
    ArrayList<String> arrayProp = confManager.getPropertyAsList("test.arrayKey", Arrays.asList(""));
    
    confManager.addChangeListener("test.changeKeyTest", new ConfManager.PropertyChangeHandler<String>() {
        @Override
        public void handle(String newValue) {
            System.out.println("Received new value: " + newValue);
        }
    });
