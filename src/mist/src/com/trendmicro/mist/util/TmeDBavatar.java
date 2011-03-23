package com.trendmicro.mist.util;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.trendmicro.codi.DataListener;
import com.trendmicro.codi.DataObserver;
import com.trendmicro.codi.ZNode;
import com.google.protobuf.TextFormat;
import com.trendmicro.mist.MistException;
import com.trendmicro.mist.proto.ZooKeeperInfo;
import com.trendmicro.spn.common.util.Utils;

public class TmeDBavatar implements DataListener{

	//private final int INTERVAL = 300;
	//private final int EPSILON = 45;

	public static final class BrokerInfoData {
		public String broker = null;
		public long max_mem = 0;
		public long free_mem = 0;
		public int num_exchange = 0;
		public int total_consumer = 0;
		public int total_producer = 0;
		public long total_msg_pendding = 0;
		public long total_msg_in = 0;
		public long total_msg_out = 0;
		public int cpu = 0;
		public int mem = 0;
		public int up_date = 0;
		public int up_hour = 0;
		public int up_min = 0;
		public int up_sec = 0;
		public long timestamp = 0;
		public long inc_msg_pendding = 0;
		public long inc_msg_in = 0;
		public long inc_msg_out = 0;
		public long stability = 0;
	}

	public static final class BrokerStatistic {
		public int broker_id;
		public int stability = 0;
		public int maximum_producer = 0;
		public int maximum_consumer = 0;
		public int maximum_exchange = 0;
		public long total_msg_pendding = 0;
		public long total_msg_in = 0;
		public long total_msg_out = 0;
		public long timestamp = 0;
	}

	public static final class ExchangeInfoData {
		public String name = null;
		public String host = null;
		public long timestamp = 0;
		public String type = null;
		public int AvgNumActiveConsumers = 0;
		public int AvgNumBackupConsumers = 0;
		public int AvgNumConsumers = 0;
		public long AvgNumMsgs = 0;
		public long AvgTotalMsgBytes = 0;
		public String ConnectionID = null;
		public long DiskReserved = 0;
		public long DiskUsed = 0;
		public int DiskUtilizationRatio = 0;
		public long MsgBytesIn = 0;
		public long MsgBytesOut = 0;
		public int NumActiveConsumers = 0;
		public int NumBackupConsumers = 0;
		public int NumConsumers = 0;
		public int NumWildcards = 0;
		public int NumWildcardConsumers = 0;
		public int NumWildcardProducers = 0;
		public long NumMsgs = 0;
		public long NumMsgsRemote = 0;
		public long NumMsgsHeldInTransaction = 0;
		public long NumMsgsIn = 0;
		public long NumMsgsOut = 0;
		public long NumMsgsPendingAcks = 0;
		public int NumProducers = 0;
		public long PeakMsgBytes = 0;
		public int PeakNumActiveConsumers = 0;
		public int PeakNumBackupConsumers = 0;
		public int PeakNumConsumers = 0;
		public long PeakNumMsgs = 0;
		public long PeakTotalMsgBytes = 0;
		public String NextMessageID = null;
		public int State = 0;
		public String StateLabel = null;
		public boolean Temporary = false;
		public long TotalMsgBytes = 0;
		public long TotalMsgBytesRemote = 0;
		public long TotalMsgBytesHeldInTransaction = 0;
	}
	
	public static final class ClientData {
		public String type = null;
		public String Host = null;
		public long RealID = 0;
		public int ExchangeID = 0;
		public long NumMsg = 0;
		public long NumMsgPending = 0;
		public long LastUpdate = 0;
		public long CreateTime = 0;
		public long LastAckTime = 0;		
	}

    private static Log logger = LogFactory.getLog(TmeDBavatar.class);

    public static final int DB_UPDATE_INTERVAL = 300*1000; // 2 mins

    public static final String ZNODE_TME_DB = "/tme2/global/portal_db";

    // define the driver to use
    public static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";

    // the database name
    public static String DB_NAME = "tme20db";

	// MySQL tables
    private static final String MYSQL_BROKER =
        "CREATE TABLE IF NOT EXISTS BROKER"
        + "(BROKER_ID INT AUTO_INCREMENT PRIMARY KEY,"
        + " NAME VARCHAR(512) NOT NULL,"
        + " IP VARCHAR(32) NOT NULL,"
        + " PORT VARCHAR(6) NOT NULL,"
        + " TYPE VARCHAR(32) NOT NULL,"
        + " VERSION VARCHAR(64),"
        + " UNIQUE (IP, PORT));";

    private static final String MYSQL_BROKER_HISTORY =
        "CREATE TABLE IF NOT EXISTS BROKER_HISTORY  "
        + "(HISTORY_ID INT AUTO_INCREMENT PRIMARY KEY,"
        + " BROKER_ID INT NOT NULL REFERENCES BROKER(BROKER_ID),"
        + " LASTUPDATE TIMESTAMP NOT NULL,"
        + " MAX_MEM BIGINT,"
        + " FREE_MEM BIGINT,"
        + " NUM_EXCHANGE INT,"
        + " NUM_PRODUCER INT,"
        + " NUM_CONSUMER INT,"
        + " TOTAL_MSG_PENDDING BIGINT,"
        + " TOTAL_MSG_IN BIGINT,"
        + " TOTAL_MSG_OUT BIGINT,"
        + " CPU INT,"
        + " MEM INT,"
        + " UP_DATE INT,"
        + " UP_HOUR INT,"
        + " UP_MIN INT,"
        + " UP_SEC INT,"
        + " INC_MSG_PENDDING BIGINT,"
        + " INC_MSG_IN BIGINT,"
        + " INC_MSG_OUT BIGINT,"
        + " STABILITY BIGINT);";

    private static final String MYSQL_BROKER_STATISTICS =
    	"CREATE TABLE IF NOT EXISTS BROKER_STATISTICS  "
    	+"(ID INT AUTO_INCREMENT PRIMARY KEY,"
    	+" BROKER_ID INT NOT NULL REFERENCES BROKER(BROKER_ID),"
    	+" STABILITY INT,"
    	+" MAX_EXCHANGE INT,"
    	+" MAX_PRODUCER INT,"
    	+" MAX_CONSUMER INT,"
    	+" TOTAL_MSG_PENDDING BIGINT,"
    	+" TOTAL_MSG_IN BIGINT,"
        +" TOTAL_MSG_OUT BIGINT,"
        +" LASTUPDATE TIMESTAMP NOT NULL);";

    private static final String MYSQL_EXCHANGE =
        "CREATE TABLE IF NOT EXISTS EXCHANGE "
        + "(EXCHANGE_ID INT AUTO_INCREMENT PRIMARY KEY, "
        + "EXC_NAME VARCHAR(512) NOT NULL COLLATE latin1_bin, "
        + "EXC_MAX_MSG_NUM BIGINT, "
        + "EXC_MAX_MSG_BYTES BIGINT, "
        + "EXC_TYPE CHAR(1) NOT NULL, "
        + "EXC_TEMPORARY CHAR(1), "
        + "EXC_FIXED CHAR(1), " 
        + "UNIQUE (EXC_NAME,  EXC_TYPE));";

	private static final String MYSQL_EXCHANGE_HISTORY =
        "CREATE TABLE IF NOT EXISTS EXCHANGE_HISTORY "
        + "(HISTORY_ID INT AUTO_INCREMENT PRIMARY KEY, "
        + "EXCHANGE_ID INT NOT NULL REFERENCES EXCHANGE(EXCHANGE_ID), "
        + "BROKER_ID INT NOT NULL REFERENCES BROKER(BROKER_ID), "
        + "AVG_ACT_CON INT, "
        + "AVG_BACKUP_CON INT, "
        + "AVG_CON INT, "
        + "AVG_MSG BIGINT, "
        + "AVG_TOTAL_MSG_BYTE BIGINT, "
        + "CONN_ID VARCHAR(6), "
        + "DISK_RESERVED BIGINT, "
        + "DISK_USED BIGINT, "
        + "DISK_UTIL_RATIO INT, "
        + "MSG_BYTE_IN BIGINT, "
        + "MSG_BYTE_OUT BIGINT, "
        + "ACT_CON INT, "
        + "BACKUP_CON INT, "
        + "NUM_CON INT, "
        + "NUM_WILD INT, "
        + "NUM_WILD_CON INT, "
        + "NUM_WILD_PRO INT, "
        + "NUM_MSG BIGINT, "
        + "NUM_MSG_REMOTE BIGINT, "
        + "NUM_MSG_HELD_TRAN BIGINT, "
        + "NUM_MSG_IN BIGINT, "
        + "NUM_MSG_OUT BIGINT, "
        + "NUM_MSG_PEND_ACK BIGINT, "
        + "NUM_PRO INT, "
        + "PEAK_MSG_BYTE BIGINT, "
        + "PEAK_ACT_CON INT, "
        + "PEAK_BACKUP_CON INT, "
        + "PEAK_NUM_CON INT, "
        + "PEAK_NUM_MSG BIGINT, "
        + "PEAK_TOTAL_MSG_BYTE BIGINT, "
        + "NEXT_MSG_ID VARCHAR(1), "
        + "STATE INT, "
        + "STATE_LABEL VARCHAR(8), "
        + "TOTAL_MSG_BYTE BIGINT, "
        + "TOTAL_MSG_BYTE_REMOTE BIGINT, "
        + "TOTAL_MSG_BYTE_HELD_TRAN BIGINT, "
        + "LASTUPDATE TIMESTAMP NOT NULL );";

	private static final String MYSQL_FORWARDER
		= "CREATE TABLE IF NOT EXISTS FORWARDER( "
		+ "FORWARDER_ID INT AUTO_INCREMENT PRIMARY KEY, "
		+ "FROM_ID INT NOT NULL, "
		+ "TO_ID INT NOT NULL, "
		+ "UNIQUE(FROM_ID, TO_ID));";

	private static final String MYSQL_FORWARDER_HISTORY
		= "CREATE TABLE IF NOT EXISTS FORWARDER_HISTORY( "
		+ " HISTORY_ID INT AUTO_INCREMENT PRIMARY KEY,"
		+ " FORWARDER_ID INT NOT NULL REFERENCES FORWARDER(FORWARDER_ID),"
		+ " BROKER_ID INT NOT NULL REFERENCES BROKER(BROKER_ID), "
		+ " LASTUPDATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP );";
	
	private static final String MYSQL_CLIENT
	= "CREATE TABLE IF NOT EXISTS CLIENT( "
	+ "CLIENT_ID INT AUTO_INCREMENT PRIMARY KEY, "
	+ "CLIENT_TYPE CHAR(1) NOT NULL, "
	+ "REAL_ID BIGINT NOT NULL, "
	+ "EXCHANGE_ID INT NOT NULL REFERENCES EXCHANGE(EXCHANGE_ID), "
	+ "HOST VARCHAR(32) NOT NULL,"
	+ "NUM_MSG BIGINT, "
	+ "NUM_MSG_PENDING BIGINT, "
	+ "CREATETIME TIMESTAMP NOT NULL, "	
	+ "LASTACKTIME TIMESTAMP NOT NULL, "
	+ "LASTUPDATE TIMESTAMP NOT NULL);";
	
	private static final String PRES_CLIENT =
		"insert into CLIENT(" +
		"CLIENT_TYPE, REAL_ID, EXCHANGE_ID, HOST, NUM_MSG, NUM_MSG_PENDING, CREATETIME, LASTACKTIME, LASTUPDATE)" +
		"values (?,?,?,?,?,?,?,?,?)";

	private static final String PRES_EXCHANGE_HIS =
	        "insert into EXCHANGE_HISTORY(" +
	        "EXCHANGE_ID, BROKER_ID, AVG_ACT_CON, AVG_BACKUP_CON, AVG_CON, " +
	        "AVG_MSG, AVG_TOTAL_MSG_BYTE, CONN_ID, DISK_RESERVED, DISK_USED, " +
	        "DISK_UTIL_RATIO, MSG_BYTE_IN, MSG_BYTE_OUT, ACT_CON, BACKUP_CON, " +
	        "NUM_CON, NUM_WILD, NUM_WILD_CON, NUM_WILD_PRO, NUM_MSG, " +
	        "NUM_MSG_REMOTE, NUM_MSG_HELD_TRAN, NUM_MSG_IN, NUM_MSG_OUT, NUM_MSG_PEND_ACK, " +
	        "NUM_PRO, PEAK_MSG_BYTE, PEAK_ACT_CON, PEAK_BACKUP_CON, PEAK_NUM_CON, " +
	        "PEAK_NUM_MSG, PEAK_TOTAL_MSG_BYTE, NEXT_MSG_ID, STATE, STATE_LABEL, " +
	        "TOTAL_MSG_BYTE, TOTAL_MSG_BYTE_REMOTE, TOTAL_MSG_BYTE_HELD_TRAN, LASTUPDATE)" +
	        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String PRES_BROKER =
        "insert into BROKER(NAME, IP, PORT, TYPE, VERSION) values (?,?,?,?,?)";

    private static final String PRES_BROKER_HIS =
        "insert into BROKER_HISTORY(BROKER_ID, MAX_MEM, FREE_MEM, " +
        "NUM_EXCHANGE, NUM_PRODUCER, NUM_CONSUMER, TOTAL_MSG_PENDDING, TOTAL_MSG_IN, TOTAL_MSG_OUT, CPU, MEM, UP_DATE, UP_HOUR, UP_MIN, UP_SEC," +
        "INC_MSG_PENDDING, INC_MSG_IN, INC_MSG_OUT, STABILITY, LASTUPDATE) " +
        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String PRES_BROKER_STAT =
    	"insert into BROKER_STATISTICS(BROKER_ID, STABILITY, MAX_EXCHANGE, MAX_PRODUCER, MAX_CONSUMER, TOTAL_MSG_IN, TOTAL_MSG_OUT, TOTAL_MSG_PENDDING, LASTUPDATE) " +
    	"values (?,?,?,?,?,?,?,?,?)";

    private static final String PRES_FORWARDER =
        "insert into FORWARDER(FROM_ID, TO_ID) values (?,?)";

    private static final String PRES_FORWARDER_HIS =
        "insert into FORWARDER_HISTORY(FORWARDER_ID, BROKER_ID) values (?,?)";

    private static final String PRES_EXCHANGE =
        "insert into EXCHANGE(EXC_NAME, EXC_TYPE, EXC_MAX_MSG_NUM, EXC_MAX_MSG_BYTES) values (?,?,?,?)";

    private static final String PRES_UPDATE_EXCHANGE =
        "update EXCHANGE set EXC_MAX_MSG_NUM=?, EXC_MAX_MSG_BYTES=? where EXC_NAME=?";


    private Connection m_dbConnection = null;
    private ZNode dbNode = null;
    private DataObserver obs = null;

    private String driverDB = null;
    private String connectionURL = null;
    private String createBrokerTable = MYSQL_BROKER;
    private String createBrokerHisTable= MYSQL_BROKER_HISTORY;
    private String createExchangeTable = MYSQL_EXCHANGE;
    private String createExchangeHisTable = MYSQL_EXCHANGE_HISTORY;
    private String createForwarderTable = MYSQL_FORWARDER;
    private String createForwarderHisTable = MYSQL_FORWARDER_HISTORY;
    private String createClientTable = MYSQL_CLIENT;

    private PreparedStatement m_BrokerPS = null;
    private PreparedStatement m_BrokerHistoryPS = null;
    private PreparedStatement m_BrokerStatPS = null;
    private PreparedStatement m_ForwarderPS = null;
    private PreparedStatement m_ForwarderHisPS = null;
    private PreparedStatement m_ExchangePS = null;
    private PreparedStatement m_ExchangeHisPS = null;
    private PreparedStatement m_UpExchangePS = null;
    private PreparedStatement m_ClientPS = null;

    private static TmeDBavatar m_theSingleton = null;

    public static synchronized TmeDBavatar getInstance() {
        if (null == m_theSingleton)
        	m_theSingleton = new TmeDBavatar();

        return m_theSingleton;
    }

    private TmeDBavatar() {
    	dbNode = new ZNode(ZNODE_TME_DB);
    	obs = new DataObserver(ZNODE_TME_DB, this, false, 0);
        obs.start();
    }

    @Override
    protected void finalize() throws Throwable {
        closeDB();
    }

    public synchronized boolean isConnectionReady() {
		// If db connection is not existed, try to open it.
		if (null == m_dbConnection)
			return openDB();
		else
			return true;
    }

    public synchronized boolean openDB() {
		// if DB connection is existed, close it first.
		if (null != m_dbConnection)
			closeDB();

		try {
			// Check node is existed
            if(!dbNode.exists())
                return false;

            byte[] data = dbNode.getContent();
            ZooKeeperInfo.PortalDB.Builder db_builder = ZooKeeperInfo.PortalDB.newBuilder();
            TextFormat.merge(new String(data), db_builder);
            ZooKeeperInfo.PortalDB portal_db = db_builder.build();

			driverDB = MYSQL_DRIVER;
			connectionURL = String.format(
					"jdbc:mysql://%s:%s/%s?user=%s&password=%s", portal_db
							.getHost(), portal_db.getPort(), portal_db
							.getName(), portal_db.getUser(), portal_db
							.getPassword());

			Class.forName(driverDB);
			m_dbConnection = DriverManager.getConnection(connectionURL);

			// Create the tables if necessary.
			createTables();

			// Create prepare statement
			m_BrokerPS = m_dbConnection.prepareStatement(PRES_BROKER);
			m_BrokerHistoryPS = m_dbConnection.prepareStatement(PRES_BROKER_HIS);
			m_BrokerStatPS = m_dbConnection.prepareStatement(PRES_BROKER_STAT);
			m_ForwarderPS = m_dbConnection.prepareStatement(PRES_FORWARDER);
			m_ForwarderHisPS = m_dbConnection.prepareStatement(PRES_FORWARDER_HIS);
			m_ExchangePS = m_dbConnection.prepareStatement(PRES_EXCHANGE);
			m_ExchangeHisPS = m_dbConnection.prepareStatement(PRES_EXCHANGE_HIS);
			m_UpExchangePS = m_dbConnection.prepareStatement(PRES_UPDATE_EXCHANGE);
			m_ClientPS = m_dbConnection.prepareStatement(PRES_CLIENT);

			logger.info(connectionURL + " loaded successfully");
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(String.format("`%s' loaded failed: %s%n%s",
					connectionURL, e.getMessage(), Utils.convertStackTrace(e)));
			// Failed case, clean everything...
			closeDB();
			return false;
		}
		return true;
    }

    private synchronized void closeDB() {
		try {
			if (null != m_BrokerPS) {
				m_BrokerPS.close();
				m_BrokerPS = null;
			}
			if (null != m_BrokerHistoryPS) {
				m_BrokerHistoryPS.close();
				m_BrokerHistoryPS = null;
			}
			if (null != m_ForwarderPS) {
				m_ForwarderPS.close();
				m_ForwarderPS = null;
			}
			if (null != m_ForwarderHisPS) {
				m_ForwarderHisPS.close();
				m_ForwarderHisPS = null;
			}
			if (null != m_ExchangePS) {
				m_ExchangePS.close();
				m_ExchangePS = null;
			}
			if (null != m_ExchangeHisPS) {
				m_ExchangeHisPS.close();
				m_ExchangeHisPS = null;
			}
			if (null != m_dbConnection) {
				m_dbConnection.close();
				m_dbConnection = null;
			}
			logger.info("Close DB");
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

    }

    private void createTables() throws SQLException {
		Statement stat = m_dbConnection.createStatement();
		DatabaseMetaData metaData = m_dbConnection.getMetaData();
		ResultSet rs = metaData.getTables(null, null, null, null);
		List<String> tablesName = new ArrayList<String>();

		while (rs.next()) {
			// System.out.println("Table name:" +
			// rs.getString("Tlog4j.logger.com.trendmicro.mistABLE_NAME"));
			tablesName.add(rs.getString("TABLE_NAME"));
		}

		if (!tablesName.contains("BROKER")) {
			stat.execute(createBrokerTable);
			// System.out.println("Create Broker Config table ok");
		}

		if (!tablesName.contains("BROKER_HISTORY")) {
			stat.execute(createBrokerHisTable);
			// System.out.println("Create Broker History table ok");
		}

		if (!tablesName.contains("BROKER_STATISTICS")) {
			stat.execute(MYSQL_BROKER_STATISTICS);
			// System.out.println("Create Broker History table ok");
		}

		if (!tablesName.contains("EXCHANGE")) {
			stat.execute(createExchangeTable);
			// System.out.println("Create forwarder table ok");
		}

		if (!tablesName.contains("EXCHANGE_HISTORY")) {
			stat.execute(createExchangeHisTable);
			// System.out.println("Create forwarder table ok");
		}

		if (!tablesName.contains("FORWARDER")) {
			stat.execute(createForwarderTable);
			// System.out.println("Create forwarder table ok");
		}

		if (!tablesName.contains("FORWARDER_HISTORY")) {
			stat.execute(createForwarderHisTable);
			// System.out.println("Create forwarder table ok");
		}
		
		if (!tablesName.contains("CLIENT")) {
			stat.execute(createClientTable);
			// System.out.println("Create forwarder table ok");
		}

		rs.close();
		stat.close();
    }

    public synchronized boolean insertBroker(ZooKeeperInfo.Broker broker) {
        if(!isConnectionReady() || null == m_BrokerPS)
            return false;

        try {
			InetAddress addr = InetAddress.getLocalHost();
			m_BrokerPS.setString(1, addr.getHostName());
			m_BrokerPS.setString(2, broker.getHost());
			m_BrokerPS.setString(3, broker.getPort());
			m_BrokerPS.setString(4, broker.getBrokerType());
			m_BrokerPS.setString(5, broker.getVersion());
			m_BrokerPS.executeUpdate();

            //logger.info("Insert a record in broker ok");
        }
        catch(Exception ex) {
        	handleError(ex);
            return false;
        }

        return true;
    }

    public synchronized boolean insertBrokerRecord(BrokerInfoData data) {
        if (!isConnectionReady() || null == m_BrokerHistoryPS)
            return false;

        try {
            int broker_id = queryBrokerID(data.broker);
            if (-1 == broker_id)
                return false;

            long diff_time =  0;

            try {
                String query = String.format("select * from BROKER_HISTORY where BROKER_ID='%d' order by LASTUPDATE desc limit 1", broker_id);
                Statement stat = m_dbConnection.createStatement();
                ResultSet rs = stat.executeQuery(query);
                boolean isFirstRec = true;

                while(rs.next()) {
                	data.total_msg_in = rs.getLong("TOTAL_MSG_IN") + data.inc_msg_in;
                	data.total_msg_out = rs.getLong("TOTAL_MSG_OUT") + data.inc_msg_out;

                	data.inc_msg_pendding = data.total_msg_pendding - rs.getLong("TOTAL_MSG_PENDDING");
                	Calendar rightNow = Calendar.getInstance();
                	diff_time = ((rightNow.getTimeInMillis()) - rs.getTimestamp(3).getTime())/1000;
                	isFirstRec = false;
                	break;
                }
    			rs.close();
                stat.close();

                if (isFirstRec) {
                	data.total_msg_in = data.inc_msg_in;
                	data.total_msg_out = data.inc_msg_out;
                	data.inc_msg_pendding = data.total_msg_pendding;
                }
            }
            catch(Exception ex) {
            	handleError(ex);
                return false;
            }

            m_BrokerHistoryPS.setInt(1, broker_id);
            m_BrokerHistoryPS.setLong(2, data.max_mem);
            m_BrokerHistoryPS.setLong(3, data.free_mem);
            m_BrokerHistoryPS.setInt(4, data.num_exchange);
            m_BrokerHistoryPS.setInt(5, data.total_producer);
            m_BrokerHistoryPS.setInt(6, data.total_consumer);
            m_BrokerHistoryPS.setLong(7, data.total_msg_pendding);
            m_BrokerHistoryPS.setLong(8, data.total_msg_in);
            m_BrokerHistoryPS.setLong(9, data.total_msg_out);
            m_BrokerHistoryPS.setInt(10, data.cpu);
            m_BrokerHistoryPS.setInt(11, data.mem);
            m_BrokerHistoryPS.setInt(12, data.up_date);
            m_BrokerHistoryPS.setInt(13, data.up_hour);
            m_BrokerHistoryPS.setInt(14, data.up_min);
            m_BrokerHistoryPS.setInt(15, data.up_sec);
            m_BrokerHistoryPS.setLong(16, data.inc_msg_pendding);
            m_BrokerHistoryPS.setLong(17, data.inc_msg_in);
            m_BrokerHistoryPS.setLong(18, data.inc_msg_out);
            m_BrokerHistoryPS.setLong(19, diff_time);
            m_BrokerHistoryPS.setTimestamp(20, new java.sql.Timestamp(data.timestamp));
            m_BrokerHistoryPS.executeUpdate();

            //logger.info("Insert a record in broker history ok");
        }
        catch(Exception ex) {
        	handleError(ex);
            return false;
        }

        return true;
    }

    public synchronized boolean insertExchange(String exchangeName, String exchangeType, long maxMsgNum, long maxMsgBytes)
    {
        if (!isConnectionReady() || null == m_ExchangePS)
            return false;

        try {
            m_ExchangePS.setString(1, exchangeName);
            m_ExchangePS.setString(2, exchangeType);
            m_ExchangePS.setLong(3, maxMsgNum);
            m_ExchangePS.setLong(4, maxMsgBytes);
            m_ExchangePS.executeUpdate();

            //logger.info("Insert a record in exchange ok");
        }
        catch(Exception ex) {
        	handleError(ex);
            return false;
        }
        return true;
    }

    public synchronized boolean updateExchange(String exchangeName, String exchangeType, long maxMsgNum, long maxMsgBytes)
    {
        if (!isConnectionReady() || null == m_UpExchangePS)
            return false;

        try {
            String query = String.format("select EXC_MAX_MSG_NUM, EXC_MAX_MSG_BYTES from EXCHANGE where EXC_NAME='%s' and EXC_TYPE='%s'", exchangeName, exchangeType);
            Statement stat = m_dbConnection.createStatement();
            ResultSet rs = stat.executeQuery(query);

            while(rs.next()) {
                if (rs.getLong(1) != maxMsgNum || rs.getLong(2) != maxMsgBytes) {
                    m_UpExchangePS.setLong(1, maxMsgNum);
                    m_UpExchangePS.setLong(2, maxMsgBytes);
                    m_UpExchangePS.setString(3, exchangeName);
                    m_UpExchangePS.executeUpdate();
                }
                break;
            }

			rs.close();
            stat.close();
            //logger.info("Update a record in exchange ok");
        }
        catch(Exception ex) {
        	handleError(ex);
            return false;
        }
        return true;
    }

    public synchronized boolean insertExchangeRecord(ExchangeInfoData e) {
        if (!isConnectionReady() || null == m_ExchangeHisPS)
            return false;

        try {
            int broker_id = queryBrokerID(e.host);
            int exchange_id = queryExchangeID(e.name, e.type);
            if (-1 == broker_id || -1 == exchange_id)
                return false;

            m_ExchangeHisPS.setInt(1, exchange_id);
            m_ExchangeHisPS.setInt(2, broker_id);
            m_ExchangeHisPS.setInt(3, e.AvgNumActiveConsumers);
            m_ExchangeHisPS.setInt(4, e.AvgNumBackupConsumers);
            m_ExchangeHisPS.setInt(5, e.AvgNumConsumers);
            m_ExchangeHisPS.setLong(6, e.AvgNumMsgs);
            m_ExchangeHisPS.setLong(7, e.AvgTotalMsgBytes);
            m_ExchangeHisPS.setString(8, e.ConnectionID);
            m_ExchangeHisPS.setLong(9, e.DiskReserved);
            m_ExchangeHisPS.setLong(10, e.DiskUsed);
            m_ExchangeHisPS.setInt(11, e.DiskUtilizationRatio);
            m_ExchangeHisPS.setLong(12, e.MsgBytesIn);
            m_ExchangeHisPS.setLong(13, e.MsgBytesOut);
            m_ExchangeHisPS.setInt(14, e.NumActiveConsumers);
            m_ExchangeHisPS.setInt(15, e.NumBackupConsumers);
            m_ExchangeHisPS.setInt(16, e.NumConsumers);
            m_ExchangeHisPS.setInt(17, e.NumWildcards);
            m_ExchangeHisPS.setInt(18, e.NumWildcardConsumers);
            m_ExchangeHisPS.setInt(19, e.NumWildcardProducers);
            m_ExchangeHisPS.setLong(20, e.NumMsgs);
            m_ExchangeHisPS.setLong(21, e.NumMsgsRemote);
            m_ExchangeHisPS.setLong(22, e.NumMsgsHeldInTransaction);
            m_ExchangeHisPS.setLong(23, e.NumMsgsIn);
            m_ExchangeHisPS.setLong(24, e.NumMsgsOut);
            m_ExchangeHisPS.setLong(25, e.NumMsgsPendingAcks);
            m_ExchangeHisPS.setInt(26, e.NumProducers);
            m_ExchangeHisPS.setLong(27, e.PeakMsgBytes);
            m_ExchangeHisPS.setInt(28, e.PeakNumActiveConsumers);
            m_ExchangeHisPS.setInt(29, e.PeakNumBackupConsumers);
            m_ExchangeHisPS.setInt(30, e.PeakNumConsumers);
            m_ExchangeHisPS.setLong(31, e.PeakNumMsgs);
            m_ExchangeHisPS.setLong(32, e.PeakTotalMsgBytes);
            m_ExchangeHisPS.setString(33, e.NextMessageID);
            m_ExchangeHisPS.setInt(34, e.State);
            m_ExchangeHisPS.setString(35, e.StateLabel);
            m_ExchangeHisPS.setLong(36, e.TotalMsgBytes);
            m_ExchangeHisPS.setLong(37, e.TotalMsgBytesRemote);
            m_ExchangeHisPS.setLong(38, e.TotalMsgBytesHeldInTransaction);
            m_ExchangeHisPS.setTimestamp(39, new java.sql.Timestamp(e.timestamp));
            m_ExchangeHisPS.executeUpdate();

            //logger.info("Insert a record in exchange history ok");
        }
        catch(Exception ex) {
        	handleError(ex);
            return false;
        }

        return true;
    }

    public int queryBrokerID(String broker_name) throws Exception{
    	if (!isConnectionReady())
			throw new MistException(String.format("unable to open db connection"));

    	int id = -1;
		Statement stat = null;
		ResultSet rs = null;

		try {
			String query = String.format("select * from BROKER where IP = '%s'",broker_name);
			stat = m_dbConnection.createStatement();
			rs = stat.executeQuery(query);

			while (rs.next()) {
				id = rs.getInt(1);
				break;
			}
		}
		catch (Exception e) {
			throw e;
		}
		finally {
        	if (null != rs) rs.close();
            if (null != stat) stat.close();
		}
        return id;
    }

    public int queryExchangeID(String exchangeName, String exchangeType) throws Exception{
        if(!isConnectionReady())
            throw new MistException(String.format("unable to open db connection"));

       	int id = -1;
		Statement stat = null;
		ResultSet rs = null;

        try {
            String query = String.format("select * from EXCHANGE where EXC_NAME = '%s' and EXC_TYPE = '%s'", exchangeName,  exchangeType);
            stat = m_dbConnection.createStatement();
            rs = stat.executeQuery(query);

            while(rs.next()) {
                id = rs.getInt(1);
                break;
            }
        }
        catch(Exception e) {
            throw e;
        }
        finally {
            if (null != rs) rs.close();
            if (null != stat) stat.close();
        }
        return id;
    }

    @Override
    public void onDataChanged(String parentPath, Map<String, byte[]> changeMap) {
        if(parentPath.compareTo(ZNODE_TME_DB) == 0){
            if(changeMap.get("") == null) {  //node deleted
                closeDB();
                logger.info("report DB is turned off");
            }
            else {                           //node created or changed
                openDB();
                logger.info("report DB is turned on");
            }
        }
    }

    // Query method
    public List<ZooKeeperInfo.Broker> getAllBroker()
    {
        List<ZooKeeperInfo.Broker> brokers = new ArrayList<ZooKeeperInfo.Broker>();

        try {
            if (isConnectionReady()) {
                Statement stat = m_dbConnection.createStatement();
                ResultSet rs = stat.executeQuery("select * from BROKER");

                while (rs.next()) {
                    ZooKeeperInfo.Broker.Builder broker_builder = ZooKeeperInfo.Broker.newBuilder();
                    broker_builder.setHost(rs.getString(3));
                    broker_builder.setPort(rs.getString(4));
                    broker_builder.setBrokerType(rs.getString(5));
                    broker_builder.setVersion(rs.getString(6));
                    broker_builder.setStatus(ZooKeeperInfo.Broker.Status.ONLINE);
                    broker_builder.setReserved(false);
                    brokers.add(broker_builder.build());
                }

                rs.close();
                stat.close();
            }
        }
        catch (Exception e) {
        	handleError(e);
        }
        return brokers;
    }

    public BrokerInfoData[] getBrokerRecord(int brokerID, long from, long to) {
    	List<BrokerInfoData> bkData = new ArrayList<BrokerInfoData>();
    	try{
            if (isConnectionReady()) {
                if (brokerID != -1) {
                	Statement stat = m_dbConnection.createStatement();
                    String query = String.format("select * from BROKER_HISTORY where BROKER_ID=%d and UNIX_TIMESTAMP(LASTUPDATE) between %d and %d order by LASTUPDATE asc", brokerID, from, to);
                    ResultSet rs = stat.executeQuery(query);

                    while (rs.next()) {
                    	bkData.add(convertResultToBrokerData(rs));
                    }
                    rs.close();
                    stat.close();
                }
            }
        }
        catch (Exception e) {
        	handleError(e);
        }

    	return bkData.toArray(new BrokerInfoData[0]);
    }

    public List<ZooKeeperInfo.Loading> getAllBrokerHistory(ZooKeeperInfo.Broker broker)
    {
        List<ZooKeeperInfo.Loading> loadings = new ArrayList<ZooKeeperInfo.Loading>();
        try{
            if (isConnectionReady()) {
                int id = queryBrokerID(broker.getHost());

                if (id != -1) {
                	Statement stat = m_dbConnection.createStatement();
                    String query = String.format("select * from BROKER_HISTORY where BROKER_ID=%d", id);
                    ResultSet rs = stat.executeQuery(query);

                    while (rs.next()) {
                        long max = rs.getLong(4);
                        long free = rs.getLong(5);
                        java.sql.Timestamp date = rs.getTimestamp(3);

                        ZooKeeperInfo.Loading.Builder load_builder = ZooKeeperInfo.Loading.newBuilder();
                        load_builder.setLoading(Math.round(((float) (max - free) / max) * 100));
                        load_builder.setLastUpdate(date.getTime());
                        load_builder.setFreeMemory(free);
                        load_builder.setMaxMemory(max);
                        loadings.add(load_builder.build());
                    }
                    rs.close();
                    stat.close();
                }
            }
        }
        catch (Exception e) {
        	handleError(e);
        }

        return loadings;
    }

//  "CREATE TABLE EXCHANGE_HISTORY "
//  + "(HISTORY_ID INT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " // 1
//  + "EXCHANGE_ID INT CONSTRAINT EXC_FK REFERENCES EXCHANGE, " // 2
//  + "BROKER_ID INT CONSTRAINT EXC_BROKER_FK REFERENCES BROKER, " //3
//  + "AVG_ACT_CON INT, "  //4
//  + "AVG_BACKUP_CON INT, " //5
//  + "AVG_CON INT, " // 6
//  + "AVG_MSG BIGINT, " // 7
//  + "AVG_TOTAL_MSG_BYTE BIGINT, " //8
//  + "CONN_ID VARCHAR(6), " // 9
//  + "DISK_RESERVED BIGINT, " // 10
//  + "DISK_USED BIGINT, " // 11
//  + "DISK_UTIL_RATIO INT, " // 12
//  + "MSG_BYTE_IN BIGINT, " // 13
//  + "MSG_BYTE_OUT BIGINT, " // 14
//  + "ACT_CON INT, "  // 15
//  + "BACKUP_CON INT, " // 16
//  + "NUM_CON INT, " // 17
//  + "NUM_WILD INT, " // 18
//  + "NUM_WILD_CON INT, " // 19
//  + "NUM_WILD_PRO INT, " // 20
//  + "NUM_MSG BIGINT, " // 21
//  + "NUM_MSG_REMOTE BIGINT, " // 22
//  + "NUM_MSG_HELD_TRAN BIGINT, " // 23
//  + "NUM_MSG_IN BIGINT, " // 24
//  + "NUM_MSG_OUT BIGINT, " // 25
//  + "NUM_MSG_PEND_ACK BIGINT, " // 26
//  + "NUM_PRO INT, " // 27
//  + "PEAK_MSG_BYTE BIGINT, " // 28
//  + "PEAK_ACT_CON INT, " // 29
//  + "PEAK_BACKUP_CON INT, " // 30
//  + "PEAK_NUM_CON INT, " // 31
//  + "PEAK_NUM_MSG BIGINT, " // 32
//  + "PEAK_TOTAL_MSG_BYTE BIGINT, " // 33
//  + "NEXT_MSG_ID VARCHAR(1), " // 34
//  + "STATE INT, " // 35
//  + "STATE_LABEL VARCHAR(8), " // 36
//  + "TOTAL_MSG_BYTE BIGINT, " // 37
//  + "TOTAL_MSG_BYTE_REMOTE BIGINT, " // 38
//  + "TOTAL_MSG_BYTE_HELD_TRAN BIGINT, " // 39
//  + "LASTUPDATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP )"; // 40

    public String[] getAllExchange() {
    	List<String> exchangeList = new ArrayList<String>();
    	try {
    		Statement stat = m_dbConnection.createStatement();
            ResultSet rs = stat.executeQuery("select * from EXCHANGE order by EXC_NAME asc");

            while (rs.next()) {
            	try {
            		exchangeList.add(rs.getString(2));
            	}
            	catch (Exception e) {
            		continue;
            	}
            }
            rs.close();
            stat.close();
    	} catch (Exception e) {

    	}
    	return (String[]) exchangeList.toArray();
    }

//    public List<ExchangeInfoData> getExchangeHistory(ZooKeeperInfo.Broker broker) {
//    	List<ExchangeInfoData> lse = new ArrayList<ExchangeInfoData>();
//    	try{
//            if (isConnectionReady()) {
//                int broker_ID = queryBrokerID(broker.getHost());
//
//                if (broker_ID != -1) {
//                	Statement stat = m_dbConnection.createStatement();
//                    String query = String.format("select * from EXCHANGE_HISTORY where BROKER_ID=%d order by LASTUPDATE desc", broker_ID);
//                    ResultSet rs = stat.executeQuery(query);
//
//                    while (rs.next()) {
//                    	try {
//                    		ExchangeInfoData e = convertResultToExchangeData(rs);
//                    		e.host = broker.getHost();
//                            lse.add(e);
//                    	}
//                    	catch (Exception e) {
//                    		continue;
//                    	}
//                    }
//                    rs.close();
//                    stat.close();
//                }
//            }
//        }
//        catch (Exception ex) {
//        	handleError(ex);
//        }
//
//    	return lse;
//    }

    // Get the latest exchange data with the specified broker and exchange name.
    public ExchangeInfoData getLatestExchangeRec(String broker, String exchangeName, String exchangeType) {
    	ExchangeInfoData e = null;
    	try{
            if (isConnectionReady()) {
                int broker_ID = queryBrokerID(broker);
                if (broker_ID != -1)
                {
					int exchange_id = queryExchangeID(exchangeName, exchangeType);
					Statement stat = m_dbConnection.createStatement();
					String query = String.format("select * from EXCHANGE_HISTORY where BROKER_ID=%d and EXCHANGE_ID=%d order by LASTUPDATE desc limit 1",
									broker_ID, exchange_id);
					ResultSet rs = stat.executeQuery(query);

					while (rs.next()) {
						try {
							e = convertResultToExchangeData(rs);
							e.host = broker;
						} catch (Exception ex) {
							handleError(ex);
						}
						break;
					}
					rs.close();
					stat.close();
				}
            }
    	}
    	catch (Exception ex) {
        	handleError(ex);
        }
    	return e;
    }

//    public List<ExchangeInfoData> getExchangeHistory(ZooKeeperInfo.Broker broker, Object[] exchanges) {
//    	List<ExchangeInfoData> lse = new ArrayList<ExchangeInfoData>();
//    	try{
//            if (isConnectionReady()) {
//                int broker_ID = queryBrokerID(broker.getHost());
//                if (broker_ID != -1)
//                {
//                	for (Object exchange : exchanges)
//                	{
//                		int exchange_id = queryExchangeID((String)exchange);
//                    	Statement stat = m_dbConnection.createStatement();
//                    	String query = String.format("select * from EXCHANGE_HISTORY where BROKER_ID=%d and EXCHANGE_ID=%d order by LASTUPDATE desc", broker_ID, exchange_id);
//                    	ResultSet rs = stat.executeQuery(query);
//
//						while (rs.next())
//						{
//							try {
//								ExchangeInfoData e = convertResultToExchangeData(rs);
//								e.host = broker.getHost();
//								lse.add(e);
//							} catch (Exception e) {
//								// Do nothing
//							}
//							break;
//						}
//						rs.close();
//		                stat.close();
//                	}
//                }
//            }
//    	}
//    	catch (Exception ex) {
//        	handleError(ex);
//        }
//    	return lse;
//    }

    private void handleError(Exception e) {
        logger.error(Utils.convertStackTrace(e));
        closeDB();
    }

    private BrokerInfoData convertResultToBrokerData(ResultSet rs) throws Exception{
    	BrokerInfoData b = new BrokerInfoData();
    	try {
    		b.broker = rs.getString(2);
    		b.timestamp = rs.getTimestamp(3).getTime();
    		b.max_mem = rs.getLong(4);
    		b.free_mem = rs.getLong(5);
    		b.num_exchange = rs.getInt(6);
    		b.total_producer = rs.getInt(7);
    		b.total_consumer = rs.getInt(8);
    		b.total_msg_pendding = rs.getLong(9);
    		b.total_msg_in = rs.getLong(10);
    		b.total_msg_out = rs.getLong(11);
    		b.cpu = rs.getInt(12);
    		b.mem = rs.getInt(13);
    		b.up_date = rs.getInt(14);
    		b.up_hour = rs.getInt(15);
    		b.up_min = rs.getInt(16);
    		b.up_sec = rs.getInt(17);
    		b.inc_msg_pendding = rs.getLong(18);
    		b.inc_msg_in = rs.getLong(19);
    		b.inc_msg_out = rs.getLong(20);
    		b.stability = rs.getLong(21);
    	}
    	catch (Exception ex) {
    		logger.error(ex.getMessage());
			throw ex;
    	}
    	return b;
    }

    private ExchangeInfoData convertResultToExchangeData(ResultSet rs) throws Exception{
    	ExchangeInfoData e = new ExchangeInfoData();

		try {
			int ex_id = rs.getInt(2);

			e.AvgNumActiveConsumers 	= rs.getInt(4);
			e.AvgNumBackupConsumers 	= rs.getInt(5);
			e.AvgNumConsumers 			= rs.getInt(6);
			e.AvgNumMsgs 				= rs.getLong(7);
			e.AvgTotalMsgBytes 			= rs.getLong(8);
			e.ConnectionID 				= rs.getString(9);
			e.DiskReserved 				= rs.getLong(10);
			e.DiskUsed 					= rs.getLong(11);
			e.DiskUtilizationRatio 		= rs.getInt(12);
			e.MsgBytesIn 				= rs.getLong(13);
			e.MsgBytesOut 				= rs.getLong(14);
			e.NumActiveConsumers 		= rs.getInt(15);
			e.NumBackupConsumers 		= rs.getInt(16);
			e.NumConsumers 				= rs.getInt(17);
			e.NumWildcards 				= rs.getInt(18);
			e.NumWildcardConsumers 		= rs.getInt(19);
			e.NumWildcardProducers 		= rs.getInt(20);
			e.NumMsgs 					= rs.getLong(21);
			e.NumMsgsRemote 			= rs.getLong(22);
			e.NumMsgsHeldInTransaction	= rs.getLong(23);
			e.NumMsgsIn 				= rs.getLong(24);
			e.NumMsgsOut 				= rs.getLong(25);
			e.NumMsgsPendingAcks 		= rs.getLong(26);
			e.NumProducers 				= rs.getInt(27);
			e.PeakMsgBytes 				= rs.getLong(28);
			e.PeakNumActiveConsumers 	= rs.getInt(29);
			e.PeakNumBackupConsumers 	= rs.getInt(30);
			e.PeakNumConsumers 			= rs.getInt(31);
			e.PeakNumMsgs 				= rs.getLong(32);
			e.PeakTotalMsgBytes 		= rs.getLong(33);
			e.NextMessageID 			= rs.getString(34);
			e.State 					= rs.getInt(35);
			e.StateLabel 				= rs.getString(36);
			e.TotalMsgBytes 			= rs.getLong(37);
			e.TotalMsgBytesRemote 		= rs.getLong(38);
			e.TotalMsgBytesHeldInTransaction = rs.getLong(39);
			e.timestamp 				= rs.getTimestamp(40).getTime();

			String q = String.format("select * from EXCHANGE where EXCHANGE_ID = %d", ex_id);
			Statement se = m_dbConnection.createStatement();
			ResultSet rse = se.executeQuery(q);
			while (rse.next()) {
				e.name = rse.getString(2);
				e.type = rse.getString(3);
				break;
			}

			rse.close();
			se.close();
		} catch (Exception exe) {
			logger.error(exe.getMessage());
			throw exe;
		}
		return e;
    }
    
    public List<ExchangeInfoData> getExchangeRecord(int brokerID, int excID, long from, long to) {
    	List<ExchangeInfoData> lse = new ArrayList<ExchangeInfoData>();
    	try {
            String query = String.format(
            		"select * from EXCHANGE_HISTORY where BROKER_ID=%d and EXCHANGE_ID=%d and UNIX_TIMESTAMP(LASTUPDATE) between %d and %d order by LASTUPDATE asc",
            		brokerID, excID, from, to);

            Statement stat = m_dbConnection.createStatement();
            ResultSet rs = stat.executeQuery(query);

            while (rs.next()) {
            	ExchangeInfoData exchange = convertResultToExchangeData(rs);
            	exchange.host = String.format("%d", brokerID);
            	exchange.name = String.format("%d", excID);
            	lse.add(exchange);
            }
            rs.close();
            stat.close();	
    	}
    	catch (Exception e) {
    		e.printStackTrace();            
    	}
         
    	return lse;
    }

    public ExchangeInfoData[] getExchangeRecord(int brokerID, long from, long to) {
    	List<ExchangeInfoData> lse = new ArrayList<ExchangeInfoData>();

    	try {
    		Statement stat = m_dbConnection.createStatement();
            ResultSet rs = stat.executeQuery("select distinct EXCHANGE_ID from EXCHANGE order by EXC_NAME asc");

            while (rs.next()) {
            	int exchangeID = rs.getInt(1);
            	lse.addAll(new ArrayList<ExchangeInfoData>(getExchangeRecord(brokerID, exchangeID, from, to)));
            }

            rs.close();
            stat.close();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}

    	return lse.toArray(new ExchangeInfoData[0]);
    }

    public boolean insertBrokerStatistic(BrokerStatistic bs) {
    	if (!isConnectionReady() || null == m_BrokerStatPS)
            return false;

        try {
            m_BrokerStatPS.setInt(1, bs.broker_id);
            m_BrokerStatPS.setInt(2, bs.stability);
            m_BrokerStatPS.setInt(3, bs.maximum_exchange);
            m_BrokerStatPS.setInt(4, bs.maximum_producer);
            m_BrokerStatPS.setInt(5, bs.maximum_consumer);
            m_BrokerStatPS.setLong(6, bs.total_msg_in);
            m_BrokerStatPS.setLong(7, bs.total_msg_out);
            m_BrokerStatPS.setLong(8, bs.total_msg_pendding);
            m_BrokerStatPS.setTimestamp(9, new java.sql.Timestamp(bs.timestamp));
            m_BrokerStatPS.executeUpdate();
        }
        catch(Exception ex) {
        	handleError(ex);
            return false;
        }

        return true;
    }

    public boolean insertClient(ClientData cd) {
    	if (!isConnectionReady() || null == m_ClientPS)
            return false;
    	
    	try {
    		m_ClientPS.setString(1, cd.type);
    		m_ClientPS.setLong(2, cd.RealID);
    		m_ClientPS.setInt(3, cd.ExchangeID);
    		m_ClientPS.setString(4, cd.Host);
    		m_ClientPS.setLong(5, cd.NumMsg);
    		m_ClientPS.setLong(6, cd.NumMsgPending);
    		m_ClientPS.setTimestamp(7, new java.sql.Timestamp(cd.CreateTime));
    		m_ClientPS.setTimestamp(8, new java.sql.Timestamp(cd.LastAckTime));
    		m_ClientPS.setTimestamp(9, new java.sql.Timestamp(cd.LastUpdate));
    		m_ClientPS.executeUpdate();
    	}
    	catch (Exception ex) {
    		handleError(ex);
    		return false;
    	}
    	
    	return true;
    }

    public boolean deleteDB(java.util.Date time) {
    	try {
    		Statement stat = m_dbConnection.createStatement();
    		String sql = String.format("delete from EXCHANGE_HISTORY where UNIX_TIMESTAMP(LASTUPDATE) < %d order by LASTUPDATE asc", time.getTime());
            ResultSet rs = stat.executeQuery(sql);
            rs.close();
            sql = String.format("delete from BROKER_HISTORY where UNIX_TIMESTAMP(LASTUPDATE) < %d order by LASTUPDATE asc", time.getTime());
            rs = stat.executeQuery(sql);
            rs.close();
    	} catch (Exception e) {
        	handleError(e);
            return false;
    	}
    	return true;
    }
}
