/**
 * Copyright (c) 2008-2016, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.mit.ll.nics.processor.gml.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang.time.StopWatch;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.geotools.GML;
import org.geotools.GML.Version;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;
import org.geotools.filter.text.cql2.CQL;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

import java.io.*;


public class GMLToDBProcessor implements Processor {

    /**
     * The logger.
     */
    private static final Logger log = Logger.getLogger(GMLToDBProcessor.class.getSimpleName());
	
    private static boolean hasInitialized = false;
    
    // !!! Properties !!!
    
	/** 
	 * The type of the database this processor connects to. 
	 * Currently only supports 'postgis' 
	 */
	private String dbtype = "postgis";
	
	/** Host/IP of the database to connect to */
	private String dbhost;
	
	/**
	 * Database port to connect to.
	 */	
	private int dbport;
	
	/** Name of the database to use */
	private String dbname;
	
	/** Username used to access the database */
	private String dbuser;
	
	/** Password used to access the database */
	private String dbpassword;
	
	/** 
	 * CRS value used for the incoming GIS data
	 */
	private String crs;
        	
	/** 
	 * DataSource reset interval in minutes
	 */
	private long db_reset_interval;

	/** 
	 * The property name that contains the time in the GML
	 * Default: timestamp
	 */
	private String timestampPropertyName = "timestamp";
	
	/**
	 * The SimpleDateFormat pattern expected from the timestamp field
	 * Default: yyyy-MM-dd'T'HH:mm:ss'Z'
	 */
	private String dateFormatPattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	
	
    private String log4jPropertyFile;
	
	/**
	 * GML Version to use.
	 * <p>Options:
	 * 		<ul>
	 * 			<li>WSF1_0</li>
	 * 			<li>WSF1_1</li>
	 * 			<li>GML2</li>
	 * 			<li>GML3</li>
	 * 		</ul>
	 * </p>
	 * <p>Default: WSF1_0</li>
	 */
	private String gml_version;
	
		
	// !!! Local private use objects !!!
	
    /** 
     * The GeoTools datastore object which connects to the database specified
     * in the db_params Map 
     */
    private static DataStore datastore = null;
    
    /** The coordinate reference system used for a table */
    private static CoordinateReferenceSystem tablecrs = null;
    
    /** Unique identifier field for lookup of incoming features in the db */
    private static final String id_table_entry = "id"; // TODO: Make a property?
    
    /** GML object used for processing incoming GML messages */
    private static GML gml;
    
    /** Map of database parameters for use with the GeoTools DataStore object */
    private static Map db_params;
    
    /** Interval in milliseconds to reset the datastore */
    private static long datastore_reset_interval;
    
    /** TODO: */
    private static final StopWatch stopwatch = new StopWatch();
    
    /** TODO: */
    private static String last_table;
    
    /** String of bad GML coordinates containing only a comma */
    private static final String gml_coord_comma = "<gml:coordinates>,</gml:coordinates>";
    
    /** Number of messages that have entered the process, used for debugging */
    private static int num_msg_started = 0;
    
    /** Number of messages that have had an exception, used for debugging */
    private static int num_msg_ex = 0;
    
    /** Number of messages that have had a handled exception, used for debugging */
    private static int num_msg_h_ex = 0;

     /**
     * Public constructor
     */
    public GMLToDBProcessor() {
    }
    
    /**
     * Initializes datastore
     * 
     * @return true if datastore was successfully initialized, false otherwise
     */
    private boolean init_datastore() {
    	boolean success = false;
    	try {
    		// If no datastore is found, this will be null
    		datastore = DataStoreFinder.getDataStore(db_params);    	
    		
    		if(datastore == null) {
    			log.info("datastore was null, so no datastore was found");
    		} else {
    			success = true;
    			stopwatch.reset();
            	stopwatch.start();
    		}
    	} catch (Exception e) {
    		log.error("Unhandled exception while getting datastore: " + e.getMessage(), e);
    	}
    	
    	return success;
    }
    
    
    /**
     * Disposes of the datastore connection, and reconnects
     */
    private void reset_datastore() {
    	try {
    		log.info("\n!!! RESETTING DATASTORE !!!\n");
    		if (datastore != null) {
    			datastore.dispose();    			
    			datastore = null;
    		}
    		
    		try {
    			datastore = DataStoreFinder.getDataStore(db_params); // Throws IOException?    			
    		} catch (Exception dse) {
    			log.error("Unhandled Exception ("+dse.getMessage()+") getting datastore with db_params: " 
    					+ db_params.toString(), dse);
    		}
    		
    		
    		if (datastore == null) {
            	log.fatal("reset_datastore(): Could not connect to PostGIS DB");
            	System.exit(1);
            } else {
            	log.info("Successfully connected PostGIS DB");
            	stopwatch.reset();
            	stopwatch.start();
            }
    		
    	} catch (Exception e) {
    		log.error("Error initializing database connection.  Exiting.", e);
    		System.exit(1);
		} 
    	
    }
    
    
    /**
     * Initializes various objects with specified property values
     */
    private boolean init() {
        PropertyConfigurator.configure(log4jPropertyFile);
        
        try {
        	Logging.GEOTOOLS.setLoggerFactory("org.geotools.util.logging.Log4JLoggerFactory");
        } catch (Exception e) {
			log.error("Caught unhandled exception while setting GeoTools to use log4j" + e.getMessage(), e);
		}
        
    	boolean success = true;
    	
    	// TODO: Validate critical properties
    	
    	db_params = new HashMap();
    	db_params.put("dbtype", dbtype);
    	db_params.put("host", dbhost);
    	db_params.put("port", dbport);
    	db_params.put("database", dbname);
    	db_params.put("user", dbuser);
    	db_params.put("passwd", dbpassword);
   	
    	log.info("Using the following for database:\n" + db_params.toString());
    	
    	datastore_reset_interval = db_reset_interval * 60 * 1000;
    	log.info("Using a db_reset_interval of: " + db_reset_interval);
    	
    	// Initialize the datastore
    	//reset_datastore();
    	if(!init_datastore()) {
    		success = false;
    	}
    	
    	// TODO: If fails, exit the application?
    	try {
    		// Set the CRS
			tablecrs = CRS.decode(crs);
			log.info("Set CRS to: " + crs);
			log.info("tablecrs: " + tablecrs);
		} catch (NoSuchAuthorityCodeException e) {
			log.error("NoSuchAuthorityException while setting CoordinateReferenceSystem to: '" + crs + 
					"': " + e.getMessage(), e);
			
		} catch (FactoryException e) {
			log.error("FactoryException while setting CoordinateReferenceSystem to: '" + crs + 
					"': " + e.getMessage(), e);
			
		} catch (Exception e) {
			log.error("Caught unhandled exception while setting CoordinateReferenceSystem to: '" + crs + 
					"': " + e.getMessage(), e);
		}
    	
    	// Initialize the GML object to the specified version
    	gml = new GML(parseGMLVersion());
    	
    	hasInitialized = true;
    	
    	return success;
    }
    
    
    /**
     * Parses the gml_version property for the GeoTools GML Version to
     * instantiate the GML object with.
     * 
     * @return the proper Version value the gml_version maps to.  If the one specified is not found, a default
     *  of WFS1_0 is returned.
     */
    private Version parseGMLVersion() {
		/* Options:
	  		<li>WSF1_0</li>
  			<li>WSF1_1</li>
  			<li>GML2</li>
  			<li>GML3</li>
		 */
    	
    	Version version = null;
    	try {
    		version = Version.valueOf(gml_version);
    	} catch(Exception e) {
    		log.info("Unhandled Exception while getting GML Version to use from gml_version property. " + 
    				"Passed in value ("+ gml_version +") most likely didn't match one of the acceptable " + 
    				"values (WFS1_0, WFS1_1, GML2, GML3).");
    	} finally {
    		if(version == null) {
    			log.info("GML Version was unable to be processed from property, setting to default value of: WFS1_0");
    			version = Version.WFS1_0; // the default
    		} else {
    			log.info("Parsed GMLVersion to: " + version.name());
    		}
    	}
    	    	
    	return version;
	}   


	/**
     * Processes incoming GML messages, and inserts them into a PostGIS database
     * @param exchange The incoming exchange from the camel route
     */
    @Override
    //@SuppressWarnings({"unchecked", "unchecked", "unchecked", "unchecked"})
    public void process(Exchange exchange) {
    	
    	if(!hasInitialized && !init()) {
			log.info("Initialization failed... shutting down.");
			try {
				exchange.getContext().stop();
				System.exit(1);
			} catch (Exception e) {
				log.error("Unhandled exception while attempting to stop context: " + e.getMessage(), e);
			}
    	}
    	
    	num_msg_started++;
    	
    	SimpleFeatureIterator iterator = null;
    	SimpleFeatureCollection getfeatures = null;
    	SimpleFeatureCollection featcollection = null;
    	SimpleFeatureStore featStore = null;
    	
    	String id = null;
    	int count = 0;
    	Filter filter = null;
    	
        // get the GML message from the exchange
    	InputStream in = exchange.getIn().getBody(InputStream.class);
    	String gml_str = exchange.getIn().getBody(String.class);
        //log.info("Processing Message In : " + gml_str);
    	
        try {
        	if (stopwatch.getTime() > datastore_reset_interval) {
        		stopwatch.stop();
        		reset_datastore();
        	}
        	
        	// Check the contents of the coordinates field
        	// if it's just a comma throw an exception
        	if (gml_str.contains(gml_coord_comma))
        		throw new InvalidCoordinates();
        	
        	featcollection = gml.decodeFeatureCollection(in);
        	//log.info("GML parsed");
        	final SimpleFeatureType schema_new = featcollection.getSchema();
        	final String postgisTableName = schema_new.getName().getLocalPart();

        	iterator = featcollection.features();

            SimpleFeature feat = iterator.next();
            try {
            	id = feat.getAttribute(id_table_entry).toString();
            } catch (NullPointerException ex2) {
            	log.info("NullPointerException getting 'id_table_entry' attribute from feature: " + id_table_entry);
            }
        	Point point =  (Point) feat.getDefaultGeometry();
        	
        	
        	if ( point.getCoordinate().equals(new Coordinate(0,0)))	{
        		throw new Exception("Coordinates = 0,0");
        	}
        	
        	String feat_crs_wkt = point.getUserData().toString();
        	CoordinateReferenceSystem pointcrs = CRS.parseWKT(feat_crs_wkt);
        	
        	if(pointcrs == null) {
        		log.error("\n!!! pointcrs is null! tablecrs is:'"+tablecrs+"' !!!\n");
        	}
        	
        	MathTransform transform = CRS.findMathTransform(pointcrs, tablecrs, false);
        	point = (Point) JTS.transform(point, transform);
        	point.setUserData(null);
        	feat.setDefaultGeometry(point);
        	        	
        	if (iterator.hasNext()) {
        		log.error("more than 1 feature in GML, id: " + id);
        	}
        	      	
        	// Get incoming track's timestamp
        	Timestamp tsNew = getTimestampFromFeatureCollection(featcollection);
        	     	
        	// Don't persist a track with an invalid time
        	if(tsNew == null) {
        		log.warn("Unparseable timestamp, dropping track");
        		return;
        	}
        	
        	// Log the track to the current table's log table
        	logTrack(postgisTableName + "_log", featcollection);
        	
        	//connect to feature store
        	//featuresource is read only, cast as featurestore for write/modify
        	featStore = (SimpleFeatureStore) datastore.getFeatureSource(postgisTableName); 
        	//log.info("connected to postgis table: " + postgisTableName);
        	
        	//look for feature in table if id is not null
        	if (id != null) {
	        	filter = CQL.toFilter(id_table_entry + " = '" + id + "'");
	            getfeatures = featStore.getFeatures(filter);
	            count = getfeatures.size();
        	}
        	
        	//int count = 0;
            switch (count) { 	
	            case 0: 	//if no feature exists then create feature for postgis database
	            	//log.info("adding feature: " + id + " to db: " + postgisTableName);
	            	
	            	//add feature to database
	            	
	            	Transaction addTransaction = new DefaultTransaction("add");
	                featStore.setTransaction(addTransaction);
	                try {
	                	featStore.addFeatures(featcollection);
	                    addTransaction.commit();
	                } catch (Exception ex) {
	                    log.error("Exception adding features: " + ex.getMessage(), ex);
	                    addTransaction.rollback();
	                } finally {
	                    addTransaction.close();
	                    //log.info("transaction closed");
	                }
	            	
	            	//log.info("added feature: " + id + " to table: " + postgisTableName);
	            	break;
	            
	            case 1://if feature is in table modify feature with updated info
	            	
	            	//log.info("found feature id: " + id + " in db: " + postgisTableName);

	            	Timestamp tsCurrent = null;
	            		            	
	            	// Get existing track's timestamp
	            	tsCurrent = getTimestampFromFeatureCollection(getfeatures);
	            	  
	            	// TODO:NEW moved up for validation
	            	// Get incoming track's timestamp
	            	//tsNew = getTimestampFromFeatureCollection(featcollection);
	            	
	            	if(tsNew != null && tsCurrent != null) {
	            			
	            		if(tsNew.before(tsCurrent)) {
	            			log.info("\n\nNEW time is older than CURRENT time, not persisting track\n\n");
	            			return;
	            		}
	            		
	            	} else {
	            		
	            		// can't compare times, so just letting it through
	            		log.info("\n\n=========\nAt least one of the times was null, so can't compare:\n" +
	            				"curtime: " + tsCurrent + "\nnewtime: " + tsNew + "\n=========\n");
	            	}
	            	
	            	Transaction removeAddTransaction = new DefaultTransaction("remove_add");
	                featStore.setTransaction(removeAddTransaction);
	                try {
	                	featStore.removeFeatures(filter);
	                	featStore.addFeatures(featcollection);
	                    removeAddTransaction.commit();
	                } catch (Exception ex) {
	                    log.error("Exception during remove_add transaction: " + ex.getMessage(), ex);
	                    removeAddTransaction.rollback();
	                } finally {
	                    removeAddTransaction.close();
	                    //log.info("t2 closed");
	                }

	            	//log.info("modified feature: " + id + " in table: " + postgisTableName);
	            	
	            	break;
	            	
	            default:
	            	log.error("more than 1 feature with that name in db, id: " + id);
            }
            
            if (!postgisTableName.equals(last_table)) {
            	last_table = postgisTableName;
            	log.info("current data source: " + postgisTableName);
            }
            
        } catch (InvalidCoordinates ex) {
        	log.info("Caught invalid coordinates exception in gml: " + gml_str + " exception: " + ex);
            num_msg_h_ex++;
            
        } catch (Exception ex) {
        	
            log.error("Caught following exception processing gml: " + gml_str + " exception: " + ex);
            num_msg_ex++;
        } finally {
        	try {
        		if (num_msg_started % 1000 == 0)	{
        			log.info("number of messages started: " + num_msg_started + "   number of messages with a handled exception: " + num_msg_h_ex + "   number of messages with an unhandled exception: " + num_msg_ex);
        		}
        		
        		if (iterator != null)	{
            		iterator.close();
            		//log.info("iterator was closed");
        		}
        			
        		in.close();
        		featStore = null;
        		featcollection = null;
        		getfeatures = null;
        	} catch (Exception ex) {
        		log.error("could not close GML inputstream or feature iterator");
        	}
        }
    }
            
    
    /**
     * Logs the features to a log table, created by appending "_log" to the postgisTableName
     * 
     * @param logTableName The name of the log table to log to
     * @param features The feature collection to log
     * @return true if successfully committed track to log, false otherwise
     */
    private boolean logTrack(String logTableName, SimpleFeatureCollection features) {
    	boolean success = false;
    	
    	if(datastore == null || logTableName == null || logTableName.isEmpty()) {
    		log.warn("Required parameter(s) null, so can't connect to log table for: " + logTableName);
    		return success;
    	}
    	
    	// connect to logstore, assumed named with appended _log
    	SimpleFeatureStore logStore = null;
    	try {
    		logStore = (SimpleFeatureStore) datastore.getFeatureSource(logTableName);
    		Transaction logTransaction = new DefaultTransaction("add");
            logStore.setTransaction(logTransaction);
            try {
            	logStore.addFeatures(features);
                logTransaction.commit();
                success = true;
            } catch (Exception ex) {
                log.error("Exception adding features: " + ex.getMessage(), ex);
                logTransaction.rollback();
            } finally {
                logTransaction.close();
            }
    	} catch(IOException e) {
    		log.error("IOException connecting to log table store. No log table for : " + 
    				logTableName + "! Can't log feature");
    	} catch(Exception e) {
    		log.error("Unhandled exception while trying to connect to log table for: " + 
    				logTableName, e);
    	}
    	
    	return success;
    	
    }
    
    /**
     * Helper method to convert a "timestamp" on a feature from a String to a Timestamp. It first
     * tries creating a Timestamp from the standard format, and failing that, attempts a more generic
     * method of parsing the timestamp String
     * 
     * @param strTime Date/time string as it exists in the incoming GML track
     * 
     * @return A SQL Timestamp object set to the specified time if successful in parsing,
     * 		   null otherwise
     */
    private Timestamp getTimestampFromFeatureString(String strTime) {
    	Timestamp ts = null;
    	java.util.Date date = null;
    	SimpleDateFormat sdf = null;
    	
    	// Expected format... but should be forgiving if this doesn't work
    	
		try {
			log.debug("Using this dateFormatPattern: " + dateFormatPattern);
			sdf = new SimpleDateFormat(dateFormatPattern, Locale.US); // TODO:NEW make locale configurable
			date = sdf.parse(strTime);
			ts = new Timestamp(date.getTime());
		} catch (ParseException e) {
			log.error("Exception parsing incoming timestamp("+strTime+"): " + e.getMessage());			
		}
    	
		// If above method didn't work, then try deprecated method below
		if(ts == null) {
			log.info("Apparently received an unexpected time format, trying alternate method...");
			try {
				// try more forgiving method of getting timestamp
				// TODO: switch to more forgiving method that's not deprecated
				ts = new Timestamp(java.util.Date.parse(strTime));
				log.info("With alternate method got: " + ts);
			} catch(Exception e) {
				log.error("Exception trying to Date.parse input string("+strTime+"): " + e.getMessage());
			}
		}
    	
    	return ts;
    }
    
    
    /**
     * Attempts to extract the "timestamp" property out of the feature collection
     * 
     * @param features collection of features assumed to contain a "timestamp" property, in this use
     * 		  there is only one feature in each collection
     * 
     * @return An initialized Timestamp object if successful, null otherwise
     */
    private Timestamp getTimestampFromFeatureCollection(SimpleFeatureCollection features) {
    	Timestamp ts = null;
    	Object objTime = null;
    	org.opengis.feature.Property propTime = null;
    	
    	final SimpleFeatureIterator iter = features.features();
    	SimpleFeature feature = iter.next(); // Only looking at first/only feature
    	iter.close();
    	
		if(feature != null) {
			propTime = feature.getProperty(timestampPropertyName);
		}
		
		if(propTime != null) {			
			objTime = propTime.getValue();
			if(objTime instanceof java.lang.String){
				log.info("Got a String...");
				ts = getTimestampFromFeatureString((String)objTime);
			} else if(objTime instanceof java.sql.Timestamp) {
				log.info("Got a Timestamp...");
				ts = (Timestamp) objTime;
			}
		} else {
			log.warn("No property '"+timestampPropertyName+"' found on feature! Must specify " +
					"the name of the element in the GML containing the timestamp!");
		}
    			
		log.debug("getTimestampFromFeatureCollection: returning: " + ts);
    	return ts;
    }
    
    
    private class InvalidCoordinates extends Exception {
	    private static final String message = "Invalid coordinates field of GML";
    	public InvalidCoordinates() {
	        super(message);
	    }
	}
    
    // Property getter/setters

	public final String getDbtype() {
		return dbtype;
	}


	public final void setDbtype(final String dbtype) {
		this.dbtype = dbtype;
	}


	public final String getDbhost() {
		return dbhost;
	}


	public final void setDbhost(final String dbhost) {
		this.dbhost = dbhost;
	}


	public final int getDbport() {
		return dbport;
	}


	public final void setDbport(final int dbport) {
		this.dbport = dbport;
	}


	public final String getDbname() {
		return dbname;
	}


	public final void setDbname(final String dbname) {
		this.dbname = dbname;
	}


	public final String getDbuser() {
		return dbuser;
	}


	public final void setDbuser(final String dbuser) {
		this.dbuser = dbuser;
	}


	public final String getDbpassword() {
		return dbpassword;
	}


	public final void setDbpassword(final String dbpassword) {
		this.dbpassword = dbpassword;
	}


	public final String getCrs() {
		return crs;
	}


	public final void setCrs(final String crs) {
		this.crs = crs;
	}


	public final String getGml_version() {
		return gml_version;
	}


	public final void setGml_version(final String gml_version) {
		this.gml_version = gml_version;
	}


	public final long getDb_reset_interval() {
		return db_reset_interval;
	}


	public final void setDb_reset_interval(final long db_reset_interval) {
		this.db_reset_interval = db_reset_interval;
	}

    public String getTimestampPropertyName() {
		return timestampPropertyName;
	}

	public void setTimestampPropertyName(String timestampPropertyName) {
		this.timestampPropertyName = timestampPropertyName;
	}

	public String getDateFormatPattern() {
		return dateFormatPattern;
	}

	public void setDateFormatPattern(String dateFormatPattern) {
		this.dateFormatPattern = dateFormatPattern;
	}

	public final String getLog4jPropertyFile() {
        return log4jPropertyFile;
    }

    public final void setLog4jPropertyFile(String log4jPropertyFile) {
        this.log4jPropertyFile = log4jPropertyFile;
    }
}
