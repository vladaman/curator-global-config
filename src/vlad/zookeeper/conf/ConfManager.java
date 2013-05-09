/*
 * 
 */
package vlad.zookeeper.conf;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedHashTreeMap;
import com.netflix.curator.framework.CuratorFramework;

public class ConfManager {
	public enum CONF_DATA_FORMAT {
		JSON
	}

	public class ExampleWatcher {
		private CuratorFramework curatorFramework;
		private List<WatchmeChangeHandler> handlers = new LinkedList<WatchmeChangeHandler>();

		private final Watcher watcher = new Watcher() {
			public void process(WatchedEvent event) {
				try {
					if (event.getType() == Event.EventType.NodeDataChanged) {
						for (WatchmeChangeHandler handler : handlers) {
							handler.handle(event);
						}
					}
				} catch (Exception e) {
					logger.error("An error occurred while sending notifications to change handler");
				} finally {
					try {
						curatorFramework.getData().usingWatcher(watcher).forPath(MYWATCH_ZK_PATH);
					} catch (Exception e) {
						logger.error("Unable to reset watch on path {}", MYWATCH_ZK_PATH);
					}
				}
			}
		};

		public ExampleWatcher(CuratorFramework curatorFramework) throws Exception {
			this.curatorFramework = curatorFramework;

			// Set the initial watcher
			this.curatorFramework.getData().usingWatcher(watcher).forPath(MYWATCH_ZK_PATH);
		}

		public void addHandler(WatchmeChangeHandler handler) {
			if (handler == null) {
				throw new IllegalArgumentException("statusChangeHandler cannot be null");
			}

			handlers.add(handler);
		}

		public void clearHandlers() {
			handlers.clear();
		}
	}

	public interface PropertyChangeHandler<T> {
		void handle(T newValue);
	}

	public interface WatchmeChangeHandler {
		void handle(WatchedEvent event);
	}

	static ConfManager instance = new ConfManager();

	public static ConfManager getInstance() {
		return instance;
	}

	private LinkedHashMultimap<String, PropertyChangeHandler<Object>> changeListeners = LinkedHashMultimap.create();
	private CONF_DATA_FORMAT confSerialization = CONF_DATA_FORMAT.JSON;
	private ExampleWatcher exampleWatcher;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	String MYWATCH_ZK_PATH = "/configs"; // can be changed by calling
											// setZookeeperPath()

	private Map<String, Object> propertyMap = new ConcurrentHashMap<String, Object>();

	private CuratorFramework zooClient;

	ConfManager() {

	}

	public <T> void addChangeListener(String key, PropertyChangeHandler<T> handler) {
		this.changeListeners.put(key, (PropertyChangeHandler<Object>) handler);
	}

	private void configureWatch() throws Exception {
		this.exampleWatcher = new ExampleWatcher(zooClient);
		exampleWatcher.addHandler(new WatchmeChangeHandler() {
			public void handle(WatchedEvent event) {
				fetchProperties();
			}
		});
	}

	private void fetchProperties() {
		try {
			byte[] data = zooClient.getData().forPath(MYWATCH_ZK_PATH);
			System.out.println(new String(data));
			swapConfMap(data);
		} catch (Exception e) {
			logger.error("Unable to fetch data for path {}", MYWATCH_ZK_PATH, e);
		}
	}

	public Object getProperty(String key) {
		return this.propertyMap.get(key);
	}

	public Integer getPropertyAsInteger(String key, Integer defValue) {
		// GSON deserializes integers as double.
		if (getProperty(key) instanceof Double) {
			return ((Double) getProperty(key)).intValue();
		} else if (getProperty(key) instanceof Integer) {
			return (Integer) getProperty(key);
		} else {
			// returns default value if object is not Integer or Double
			return defValue;
		}
		// return (getProperty(key) == null) ? defValue :
		// Integer.valueOf(String.valueOf(getProperty(key)));
	}

	public <T> ArrayList<T> getPropertyAsList(String key, List<T> defValue) {
		return (ArrayList<T>) ((getProperty(key) == null) ? defValue : getProperty(key));
	}

	public String getPropertyAsString(String key, String defValue) {
		final Object prop = getProperty(key);
		System.out.println(prop instanceof String);
		if (prop != null && prop instanceof String) {
			return (String) prop;
		}
		return defValue;
	}

	public void setClient(CuratorFramework zooClient) throws Exception {
		this.zooClient = zooClient;
		if (this.zooClient.checkExists().forPath(MYWATCH_ZK_PATH) == null) {
			this.zooClient.create().forPath(MYWATCH_ZK_PATH);
			this.zooClient.setData().forPath(MYWATCH_ZK_PATH, "{}".getBytes());
			logger.warn("Configuration Path does not Exists! Created Path in Zookeeper {}", MYWATCH_ZK_PATH);
		}
		fetchProperties();
		configureWatch();
	}

	public void setDataFormat(CONF_DATA_FORMAT type) {
		this.confSerialization = type;
	}

	public void setZookeeperPath(String path) {
		this.MYWATCH_ZK_PATH = path;
	}

	private void swapConfMap(byte[] data) {
		LinkedHashTreeMap<String, Object> gson = null;
		System.out.println(data.length);

		try {
			gson = new Gson().fromJson(new String(data), LinkedHashTreeMap.class);
		} catch (Exception e) {
			logger.error("Gson Exception", e);
		}
		if (gson != null) {
			propertyMap = Collections.synchronizedMap(gson);
			for (String key : propertyMap.keySet()) {
				if (changeListeners.containsKey(key)) {
					Object newValue = propertyMap.get(key);
					Iterator<PropertyChangeHandler<Object>> itn = changeListeners.get(key).iterator();
					while (itn.hasNext()) {
						PropertyChangeHandler<Object> handler = itn.next();
						ParameterizedType obj = (ParameterizedType) handler.getClass().getGenericInterfaces()[0];
						if (obj.getActualTypeArguments()[0].equals(Integer.class) && newValue instanceof Double) {
							handler.handle(((Double) newValue).intValue());
						} else {
							try {
								handler.handle(newValue);
							} catch (Exception e) {
								logger.error("An error executing handler on key<{}>", key, e);
							}
						}
					}
				}
			}
		}
	}
}
