/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.analysis;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

/**
* @author ikaddoura
*/

public class TripAnalysisFilter implements TripFilter {
	
	private static final Logger log = Logger.getLogger(TripAnalysisFilter.class);	
	private boolean dataPreprocessed = false;
	private final Map<String, Geometry> zoneFeatures = new HashMap<>();
	
	private String zoneFile = null;
	private String zoneCRS = null;
	private final String filterName;
	private double buffer = 0.;
	private TripConsiderType tripConsiderType = TripConsiderType.OriginAndDestination;
	
	private enum TripConsiderType { OriginAndDestination, OriginOrDestination }
	
	public TripAnalysisFilter(String filterName) {
		this.filterName = filterName;
	}
	
	@Deprecated
	public TripAnalysisFilter() {
		this.filterName = "";
	}

	@Override
	public boolean considerTrip(Coord origin, Coord destination) {
		
		if (dataPreprocessed == false) {
			throw new RuntimeException("Can't use the filter without pre-processing. Aborting...");
		}
		
		if (origin == null || destination == null) {
			log.warn("Origin or destination null. Can't interpret this trip. Origin: " + origin + "--> Destination: " +  destination);
			return false;
		}
		
		if (zoneFeatures.size() == 0) {
			// no zoneFeatures loaded --> consider all trips
			return true;
		}
		
		// assuming the same CRS!
		boolean originWithinProvidedGeometry = false;
		for (Geometry geometry : zoneFeatures.values()) {
			Point point = MGC.coord2Point(origin);
			if (point.within(geometry.buffer(buffer))) {
				originWithinProvidedGeometry = true;
				break;
			}
		}
		boolean destinationWithinProvidedGeometry = false;
		for (Geometry geometry : zoneFeatures.values()) {
			Point point = MGC.coord2Point(destination);
			if (point.within(geometry.buffer(buffer))) {
				destinationWithinProvidedGeometry = true;
				break;
			}
		}
		
		if (this.tripConsiderType == TripConsiderType.OriginAndDestination) {
			if (originWithinProvidedGeometry && destinationWithinProvidedGeometry) return true;
		} else if (this.tripConsiderType == TripConsiderType.OriginOrDestination) {
			if (originWithinProvidedGeometry || destinationWithinProvidedGeometry) return true;
		} else {
			throw new RuntimeException("Unknown TripConsiderType. Aborting...");
		}
				
		return false;
	}

	@Override
	public String toFileName() {
		String fileName = "_TRIPFILTER-" + filterName;
				
		if (zoneFile == null) {
			fileName = fileName + "_all-trips-considered_no-shapefile-provided";	
			
		} else {
			if (zoneFeatures.size() == 0) {
				// no zoneFeatures loaded --> consider all trips
				fileName = fileName + "_all-trips-considered_no-zone-features";	
			} else {
				fileName = fileName + "_" +  this.tripConsiderType.toString() + "-in-zone_buffer-" + this.buffer ;
			}
		}
	
		return fileName;
	}

	public String getZoneFile() {
		return zoneFile;
	}

	public void setZoneInformation(String zoneFile, String zonesCRS) {
		this.zoneFile = zoneFile;
		this.zoneCRS  = zonesCRS;
	}
	
	public void preProcess(Scenario scenario) {
		this.dataPreprocessed = true;
	    	    
		if (scenario != null &&  zoneFile != null) {
			
			if (scenario.getNetwork() != null && this.zoneCRS != null) {
				String crsNetwork = (String) scenario.getNetwork().getAttributes().getAttribute("coordinateReferenceSystem");
		        if (!this.zoneCRS.equalsIgnoreCase(crsNetwork)) {
		        	if ( (this.zoneCRS.equalsIgnoreCase("DHDN_GK4") && crsNetwork.equalsIgnoreCase("GK4"))
		        			|| (this.zoneCRS.equalsIgnoreCase("GK4") && crsNetwork.equalsIgnoreCase("DHDN_GK4"))
		        			|| (this.zoneCRS.equalsIgnoreCase("GK4") && crsNetwork.equalsIgnoreCase("EPSG:31468"))
		        			|| (this.zoneCRS.equalsIgnoreCase("EPSG:31468") && crsNetwork.equalsIgnoreCase("GK4"))
		        			|| (this.zoneCRS.equalsIgnoreCase("DHDN_GK4") && crsNetwork.equalsIgnoreCase("EPSG:31468"))
		        			|| (this.zoneCRS.equalsIgnoreCase("EPSG:31468") && crsNetwork.equalsIgnoreCase("DHDN_GK4"))
		        			) {
		        		// should not cause any problems.
		        	} else {
		        		throw new RuntimeException("Coordinate transformation not yet implemented. Expecting shape file to have the following coordinate reference system: " + crsNetwork + " instead of " + this.zoneCRS);
				        // TODO: add coordinate transformation
		        	}
				}
			}
			
			log.info("Reading shape file...");
			Collection<SimpleFeature> features = null;
			if (zoneFile.startsWith("http")) {
				try {
					features = ShapeFileReader.getAllFeatures(new URL(zoneFile));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			} else {
				features = ShapeFileReader.getAllFeatures(zoneFile);
			}
			int counter = 0;
			for (SimpleFeature feature : features) {
                Geometry geometry = (Geometry) feature.getDefaultGeometry();
                zoneFeatures.put(String.valueOf(counter), geometry);
                counter++;
            }
			log.info("Reading shape file... Done.");	
		}
		
	}

	public void setBuffer(double buffer) {
		this.buffer = buffer;
	}

	public void setTripConsiderType(TripConsiderType tripConsiderType) {
		this.tripConsiderType = tripConsiderType;
	}

}

