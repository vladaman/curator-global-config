/*
 * 
 */
package vlad.zookeeper.conf;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vlad.zookeeper.conf.ConfManager.PropertyChangeHandler;

import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedHashTreeMap;
import com.netflix.curator.framework.CuratorFramework;

public class ConfManager<T> {
	public enum CONF_DATA_FORMAT {
		JSON
	}

	private static final String PATH = "/configs/example";
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	final String MYWATCH_ZK_PATH = "/mywatch";

	static ConfManager instance = new ConfManager();

	private CuratorFramework zooClient;
	private ExampleWatcher exampleWatcher;
	private Map<String, Object> propertyMap = new ConcurrentHashMap<String, Object>();
	private CONF_DATA_FORMAT confSerialization = CONF_DATA_FORMAT.JSON;
	private LinkedHashMultimap<String, PropertyChangeHandler<T>> changeListeners = LinkedHashMultimap.create();

	ConfManager() {

	}

	public static ConfManager getInstance() {
		return instance;
	}

	public void setClient(CuratorFramework zooClient) throws Exception {
		this.zooClient = zooClient;
		fetchProperties();
		configureWatch();
	}

	private void fetchProperties() {
		try {
			byte[] data = zooClient.getData().forPath(MYWATCH_ZK_PATH);
			swapConfMap(data);
		} catch (Exception e) {
			logger.error("Unable to fetch data for path {}", MYWATCH_ZK_PATH, e);
		}
	}

	private void configureWatch() throws Exception {
		this.exampleWatcher = new ExampleWatcher(zooClient);
		exampleWatcher.addHandler(new WatchmeChangeHandler() {
			public void handle(WatchedEvent event) {
				fetchProperties();
			}
		});
	}

	private void swapConfMap(byte[] data) {
		LinkedHashTreeMap<String, Object> gson = new Gson().fromJson(new String(data), LinkedHashTreeMap.class);
		if (gson != null) {
			propertyMap = Collections.synchronizedMap(gson);
			System.out.println(propertyMap);
			for (String key : propertyMap.keySet()) {
				if (changeListeners.containsKey(key)) {
					System.out.println(key + " has listner");
					Object newValue = propertyMap.get(key);
					Iterator<PropertyChangeHandler<T>> itn = changeListeners.get(key).iterator();
					while (itn.hasNext()) {
						PropertyChangeHandler<T> handler = itn.next();
						try {
							// BUG: Can't handle Integers casted as Double (JSON issue)
							handler.handle((T) newValue);
						} catch (Exception e) {
							logger.error("An error executing handler on key<{}>", key, e);
						}
					}
				}
			}
		}
		System.out.println(gson.get("testArray").getClass().getSimpleName());
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

	public interface WatchmeChangeHandler {
		void handle(WatchedEvent event);
	}

	public interface PropertyChangeHandler<T> {
		void handle(T newValue);
	}

	public Object getProperty(String key) {
		return this.propertyMap.get(key);
	}

	public String getPropertyAsString(String key, String defValue) {
		return (String) getProperty(key);
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

	public <T> ArrayList getPropertyAsList(String key, List<T> defValue) {
		return (ArrayList) ((getProperty(key) == null) ? defValue : getProperty(key));
	}

	public void setDataFormat(CONF_DATA_FORMAT type) {
		this.confSerialization = type;
	}

	public void addChangeListener(String key, PropertyChangeHandler<T> handler) {
		this.changeListeners.put(key, handler);
	}
}
