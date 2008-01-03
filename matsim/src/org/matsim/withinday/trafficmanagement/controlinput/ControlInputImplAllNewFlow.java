/* *********************************************************************** *
 * project: org.matsim.*
 * ControlInputImplDAccident.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.withinday.trafficmanagement.controlinput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.events.EventAgentArrival;
import org.matsim.events.EventAgentDeparture;
import org.matsim.events.EventLinkEnter;
import org.matsim.events.EventLinkLeave;
import org.matsim.events.handler.EventHandlerAgentArrivalI;
import org.matsim.events.handler.EventHandlerAgentDepartureI;
import org.matsim.events.handler.EventHandlerLinkEnterI;
import org.matsim.events.handler.EventHandlerLinkLeaveI;
import org.matsim.mobsim.QueueLink;
import org.matsim.mobsim.SimulationTimer;
import org.matsim.network.Link;
import org.matsim.network.Node;
import org.matsim.plans.Route;
import org.matsim.withinday.trafficmanagement.AbstractControlInputImpl;
import org.matsim.withinday.trafficmanagement.Accident;
import org.matsim.withinday.trafficmanagement.ControlInput;

/**
 * 
 * @author abergsten and dzetterberg
 */

/* User parameters are:
 * DISTRIBUTIONCHECK	True means that model checks traffic distribution before bottle
 * 						neck.
 * NUMBEROFFLOWEVENTS	The flow calculations are based on the last NUMBEROFFLOWEVENTS 
 * 						agents. A higher value means better predictions if congestion.
 * IGNOREDQUEUINGIME	Additional link travel times up to IGNOREDQUEUINGIME seconds 
 * 						of the links free speed travel time will not be	considered as 
 * 						a accident (temporary capacity reduction). Default is that there
 * FLOWUPDATETIME		determines how often to measure additional flows from in- and 
 * 						must be at least 20 seconds longer than the link's free speed time. 
 * 						outlinks. Default is to update every 60 seconds.
 * RESETBOTTLENECKINTERVALL The bottleneck flow is used for predictions this many 
 * 							seconds. Then the accident is forgotten and has to be detected 
 * 							again.
 * 
 */


public class ControlInputImplAllNewFlow extends AbstractControlInputImpl
	implements EventHandlerLinkLeaveI, EventHandlerLinkEnterI, 
	EventHandlerAgentDepartureI, EventHandlerAgentArrivalI, ControlInput {

//	User parameters:
	private static final boolean DISTRIBUTIONCHECKACTIVATED = false;
		
		private static final int NUMBEROFEVENTSDETECTION = 20;
		
		private static final double IGNOREDQUEUINGTIME = 20; // seconds

	private static final boolean BACKGROUNDNOISECOMPENSATIONACTIVATED = true;
	
		private static final int NUMBEROFEVENTSINOUTFLOW = 20;

	private static final boolean INCIDENTDETECTIONACTIVATED = false;

	private static final double RESETBOTTLENECKINTERVALL = 1800;

	
	private static final Logger log = Logger.getLogger(ControlInputImplAllNewFlow.class);
	
	private double predTTMainRoute;

	private double predTTAlternativeRoute;
	
	private ControlInputWriter writer;

	private Map<String, Double> ttMeasured = new HashMap<String, Double> ();
	
	
//	For distribution heterogenity check:
	private Map<String, Double> enterLinkEvents = new HashMap<String, Double>();
	
	private Map <String, Double> intraFlows = new HashMap<String, Double>();
	
	private Map <String, List<Double>> enterLinkEventTimes = new HashMap<String, List<Double>>();
	
	private Map<String, Double> capacities = new HashMap<String, Double> ();
		
//	For in/outlinks disturbance check:
	private Map <String, Double> extraFlowsMainRoute = new HashMap<String, Double>();
	
	private List<Link> inLinksMainRoute = new ArrayList<Link>();
	
	private List<Link> outLinksMainRoute = new ArrayList<Link>();

	private ArrayList<Node> nodesMainRoute = new ArrayList<Node>();
	
	private Map <String, Double> extraFlowsAlternativeRoute = new HashMap<String, Double>();

	private List<Link> inLinksAlternativeRoute = new ArrayList<Link>();

	private List<Link> outLinksAlternativeRoute = new ArrayList<Link>();

	private ArrayList<Node> nodesAlternativeRoute = new ArrayList<Node>();
	
//	private Map<String, Double> flowLinkDistances = new HashMap<String, Double>();
	
	private Map<String, Double> ttFreeSpeedUpToAndIncludingLink = new HashMap<String, Double>();
	
	private	Map<String, Double> inFlows = new HashMap<String, Double>();
	
	private	Map<String, Double> outFlows = new HashMap<String, Double>();

	private Map<String, Integer> numbersPassedOnInAndOutLinks = new HashMap<String, Integer>();
	
//	For Accident detection:
	private Link currentBottleNeckMainRoute;
	
	private Double currentBNCapacityMainRoute;
	
	private Link currentBottleNeckAlternativeRoute;
	
	private Double currentBNCapacityAlternativeRoute;
	
	private List<Accident> accidents;


	
	public ControlInputImplAllNewFlow() {
		super();
		this.writer = new ControlInputWriter();
	}
	
	@Override
	public void init() {
		super.init();
		this.writer.open();
		
//		Initialize ttMeasured with ttFreeSpeeds and linkFlows with zero.
//		Main route
		Link [] linksMainRoute = this.mainRoute.getLinkRoute();
		for (Link l : linksMainRoute) {
			String linkId = l.getId().toString();
			if (!this.intraFlows.containsKey(l.getId().toString()))  {
				this.intraFlows.put(l.getId().toString(), 0.0);
			}
			
			if (!this.ttMeasured.containsKey(l.getId().toString()))  {
				this.ttMeasured.put(l.getId().toString(), 
						this.ttFreeSpeeds.get(l.getId().toString()));
			}
			
			if (!this.capacities.containsKey(l.getId().toString()))  {
				double capacity = ((QueueLink)l).getSimulatedFlowCapacity() / SimulationTimer.getSimTickTime();
				this.capacities.put(l.getId().toString(), capacity);
			}
			
			if (!this.enterLinkEventTimes.containsKey(l.getId().toString()))  {
				List<Double> list = new LinkedList<Double>();
				this.enterLinkEventTimes.put(l.getId().toString(), list );
			}
			
			if (!this.ttFreeSpeedUpToAndIncludingLink.containsKey(l.getId().toString())) {
			double tt = sumUpTTFreeSpeed(l.getToNode(), this.mainRoute);
			this.ttFreeSpeedUpToAndIncludingLink.put(linkId, tt);
			}
		}
		currentBottleNeckMainRoute = mainRouteNaturalBottleNeck;
		currentBNCapacityMainRoute = getCapacity(mainRouteNaturalBottleNeck);
		List<Link> linksMainRouteList = Arrays.asList(linksMainRoute);
		nodesMainRoute = mainRoute.getRoute();
		for (int i = 1; i < nodesMainRoute.size() -1; i++ ) {
			Node n = nodesMainRoute.get(i);
			for (Link inLink : n.getInLinks().values()) {
				String linkId = inLink.getId().toString();
				if(!linksMainRouteList.contains(inLink)){
					double tt = sumUpTTFreeSpeed(n, this.mainRoute);
					this.ttFreeSpeedUpToAndIncludingLink.put(linkId, tt);
					inLinksMainRoute.add(inLink);
					this.inFlows.put(inLink.getId().toString(), 0.0);
					numbersPassedOnInAndOutLinks.put(inLink.getId().toString(), 0);
					this.extraFlowsMainRoute.put(linkId, 0.0);
					List<Double> list = new LinkedList<Double>();
					this.enterLinkEventTimes.put(linkId, list );
				}
			}
			for (Link outLink : n.getOutLinks().values()) {
				String linkId = outLink.getId().toString();
				if(!linksMainRouteList.contains(outLink)){
					double tt = sumUpTTFreeSpeed(n, this.mainRoute);
					this.ttFreeSpeedUpToAndIncludingLink.put(linkId, tt);
					outLinksMainRoute.add(outLink);
					this.outFlows.put(outLink.getId().toString(), 0.0);
					numbersPassedOnInAndOutLinks.put(outLink.getId().toString(), 0);
					this.extraFlowsMainRoute.put(linkId, 0.0);
					List<Double> list = new LinkedList<Double>();
					this.enterLinkEventTimes.put(linkId, list );
				}
			}
		}
		
//		Alt Route
		Link [] linksAlternativeRoute = this.alternativeRoute.getLinkRoute();
		for (Link l : linksAlternativeRoute) {
			String linkId = l.getId().toString();
			if (!this.intraFlows.containsKey(l.getId().toString()))  {
				this.intraFlows.put(l.getId().toString(), 0.0);
			}
			
			if (!this.ttMeasured.containsKey(l.getId().toString()))  {
				this.ttMeasured.put(l.getId().toString(), 
						this.ttFreeSpeeds.get(l.getId().toString()));
			}
			
			if (!this.capacities.containsKey(l.getId().toString()))  {
				double capacity = ((QueueLink)l).getSimulatedFlowCapacity() / SimulationTimer.getSimTickTime();
				this.capacities.put(l.getId().toString(), capacity);			}
			
			if (!this.enterLinkEventTimes.containsKey(l.getId().toString()))  {
				List<Double> list = new LinkedList<Double>();
				this.enterLinkEventTimes.put(l.getId().toString(), list );
			}
			if (!this.ttFreeSpeedUpToAndIncludingLink.containsKey(l.getId().toString())) {
				double tt = sumUpTTFreeSpeed(l.getToNode(), this.mainRoute);
				this.ttFreeSpeedUpToAndIncludingLink.put(linkId, tt);
			}
		}
		currentBottleNeckAlternativeRoute = altRouteNaturalBottleNeck;
		currentBNCapacityAlternativeRoute = getCapacity(altRouteNaturalBottleNeck);
		
		nodesAlternativeRoute = this.alternativeRoute.getRoute();
		List<Link> linksAlternativeRouteList = Arrays.asList(linksAlternativeRoute);
		for (int i = 1; i < nodesAlternativeRoute.size() -1; i++ ) {
			Node n = nodesAlternativeRoute.get(i);
			for (Link inLink : n.getInLinks().values()) {
				String linkId = inLink.getId().toString();
				if(!linksAlternativeRouteList.contains(inLink)){
					double tt = sumUpTTFreeSpeed(n, this.alternativeRoute);
					this.ttFreeSpeedUpToAndIncludingLink.put(linkId, tt);
					inLinksAlternativeRoute.add(inLink);
					this.outFlows.put(inLink.getId().toString(), 0.0);
					numbersPassedOnInAndOutLinks.put(inLink.getId().toString(), 0);
					this.extraFlowsAlternativeRoute.put(linkId, 0.0);
					List<Double> list = new LinkedList<Double>();
					this.enterLinkEventTimes.put(linkId, list );
//				} else{
//					System.out.println("No additional inLinks");
				}
			}
			for (Link outLink : n.getOutLinks().values()) {
				String linkId = outLink.getId().toString();
				if(!linksAlternativeRouteList.contains(outLink)){
					double tt = sumUpTTFreeSpeed(n, this.alternativeRoute);
					this.ttFreeSpeedUpToAndIncludingLink.put(linkId, tt);
					outLinksAlternativeRoute.add(outLink);
					this.outFlows.put(outLink.getId().toString(), 0.0);
					numbersPassedOnInAndOutLinks.put(outLink.getId().toString(), 0);
					this.extraFlowsAlternativeRoute.put(linkId, 0.0);
					List<Double> list = new LinkedList<Double>();
					this.enterLinkEventTimes.put(linkId, list );
//				} else{
//					System.out.println("No additional outLinks");
				}
			}
		}
	}
	
private double sumUpTTFreeSpeed(Node node, Route route) {
		
		double ttFS = 0;
		Link [] routeLinks = route.getLinkRoute();
		for (int i = 0; i < routeLinks.length; i++) {
			Link l = routeLinks[i];
			ttFS += this.ttFreeSpeeds.get(l.getId().toString());
			if ( l.getToNode() == node ) {
				break;
			}
		}
		return ttFS;
	}

//	private double getDistanceFromFirstNode(Node node, Route route) {
//		Link[] routeLinks = route.getLinkRoute();
//		double distance = 0;
//		int i=0;
//		while(!routeLinks[i].getToNode().equals(node)){
//			distance += routeLinks[i].getLength();
//			i++;
//		}
//		distance += routeLinks[i].getLength();
//		return distance;
//	}

	@Override
	public void handleEvent(final EventLinkEnter event) {
		
//		Must be done before super.handleEvent as that removes entries
		if ( this.ttMeasured.containsKey(event.linkId) ) {
			this.enterLinkEvents.put(event.agentId, event.time);
		}
		
		//handle flows on outLinks
		if (outLinksMainRoute.contains(event.link)){
			int numbersPassed = numbersPassedOnInAndOutLinks.get(event.linkId) + 1;
			numbersPassedOnInAndOutLinks.put(event.linkId, numbersPassed);
		}
		else if (outLinksAlternativeRoute.contains(event.linkId)){
			int numbersPassed = numbersPassedOnInAndOutLinks.get(event.linkId) + 1;
			numbersPassedOnInAndOutLinks.put(event.linkId, numbersPassed);
		}
		
		super.handleEvent(event);
	}
	

	@Override
	public void handleEvent(final EventLinkLeave event) {

//		Must be done before super.handleEvent as that removes entries
		if (this.ttMeasured.containsKey(event.linkId) 
				&& this.enterLinkEvents.get(event.agentId) != null) {
			Double enterTime = this.enterLinkEvents.remove(event.agentId);
			Double travelTime = event.time - enterTime;
			this.ttMeasured.put(event.linkId, travelTime);
		}
		
//		Stores [NUMBEROFFLOWEVENTS] last events and calculates flow for detection of capacity reduction
		if (this.intraFlows.containsKey(event.linkId)) {
			updateFlow(NUMBEROFEVENTSDETECTION, event);
		}
		if ( this.inLinksAlternativeRoute.contains(event.link) || this.outLinksAlternativeRoute.contains(event.link) ||
				this.inLinksMainRoute.contains(event.link) || this.outLinksMainRoute.contains(event.link) ) {
			updateFlow(NUMBEROFEVENTSINOUTFLOW, event);
		}

		super.handleEvent(event);
	}
	

	private void updateFlow(int flowResolution, EventLinkLeave event) {
		
		LinkedList<Double> list = (LinkedList<Double>) this.enterLinkEventTimes.get(event.linkId);
		if ( list.size() == flowResolution ) {
		list.removeFirst();
		list.add(event.time);
		}
		else if (1 < list.size() || list.size() < flowResolution) {
			list.add(event.time);
		} else if ( list.size() == 0 ) {
			list.add(event.time - 1);
			list.add(event.time);
		}
		else {
			System.err.println("Error: number of enter event times stored exceeds numberofflowevents!");
		}
		
//		Flow = agents / seconds:
		double flow = (list.size() - 1) / (list.getLast() - list.getFirst());
		
		if (this.intraFlows.containsKey(event.linkId)) {
			this.intraFlows.put(event.linkId, flow);
		}
		if ( this.inLinksMainRoute.contains(event.link) ) {
			double inFlow = flow;
			this.extraFlowsMainRoute.put(event.linkId,inFlow);
		}
		if ( this.outLinksMainRoute.contains(event.link) ) {
			double outFlow = -flow;
			this.extraFlowsMainRoute.put(event.linkId, outFlow);
		}
		if ( this.inLinksAlternativeRoute.contains(event.link) ) {
			double inFlow = flow;
			this.extraFlowsAlternativeRoute.put(event.linkId, inFlow);
		}
		if (this.outLinksAlternativeRoute.contains(event.link) ) {
			double outFlow = -flow;
			this.extraFlowsAlternativeRoute.put(event.linkId, outFlow);
		}
	}
	
	

	public void reset(final int iteration) {
		try {
			this.writer.writeTravelTimesPerAgent(ttMeasuredMainRoute, ttMeasuredAlternativeRoute);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.writer.close();
	}


	@Override
	public void handleEvent(final EventAgentDeparture event) {
		super.handleEvent(event);
	}

	@Override
	public void handleEvent(final EventAgentArrival event) {
		super.handleEvent(event);
	}

	public double getNashTime() {
		try {
			this.writer.writeAgentsOnLinks(this.numberOfAgents);
			this.writer.writeTravelTimesMainRoute(this.lastTimeMainRoute,
					this.predTTMainRoute);
			this.writer.writeTravelTimesAlternativeRoute(this.lastTimeAlternativeRoute,
					this.predTTAlternativeRoute);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return getPredictedNashTime();

	}
	
	// calculates the predictive time difference
	public double getPredictedNashTime() {
		
		if (this.accidents.isEmpty()) {
//			throw new UnsupportedOperationException("To use this controler an accident has to be set");
			this.predTTMainRoute = 
				getPredictedTravelTime(this.mainRoute, this.mainRouteNaturalBottleNeck);
		}
		else {
		String accidentLinkId = this.accidents.get(0).getLinkId();
		Link accidentLinkMainRoute = searchAccidentsOnRoutes(accidentLinkId);
		this.predTTMainRoute = 
			getPredictedTravelTime(this.mainRoute, accidentLinkMainRoute);
		}


		this.predTTAlternativeRoute = 
			getPredictedTravelTime(this.alternativeRoute, this.altRouteNaturalBottleNeck);

		return this.predTTMainRoute - this.predTTAlternativeRoute;
	}

	
	private double getPredictedTravelTime(final Route route,
			final Link bottleNeck) {
		
		double predictedTT;
		Link[] routeLinks = route.getLinkRoute();
		double ttFreeSpeedPart = 0.0;
		int agentsToQueueAtBottleNeck = 0;
		boolean guidanceObjectWillQueue = false;
		Link currentBottleNeck = bottleNeck;
		double currentBottleNeckCapacity;
		double ttFreeSpeedBeforeBottleNeck;
		
		if (INCIDENTDETECTIONACTIVATED) { 
			currentBottleNeck = getDetectedBottleNeck(route);
			currentBottleNeckCapacity = getIncidentCapacity(route);
			for ( int i = routeLinks.length - 1; i >= 0; i-- ) {
				String linkId = routeLinks[i].getId().toString();

				if ( this.ttMeasured.get(linkId) > this.ttFreeSpeeds.get(linkId) + IGNOREDQUEUINGTIME )  {
					currentBottleNeck = routeLinks[i];
					setIncidentLink(currentBottleNeck, route);
					currentBottleNeckCapacity = getFlow(currentBottleNeck);
					setIncidentCapacity(currentBottleNeckCapacity, route);
				
//				do not check links before current bottleneck
				break; 
				}
				else if (SimulationTimer.getTime()%RESETBOTTLENECKINTERVALL == 0) {
					currentBNCapacityAlternativeRoute = getCapacity(altRouteNaturalBottleNeck);
					currentBNCapacityMainRoute = getCapacity(mainRouteNaturalBottleNeck);
					currentBottleNeckAlternativeRoute = altRouteNaturalBottleNeck;
					currentBottleNeckMainRoute = mainRouteNaturalBottleNeck;
				}	
			}
		}
		
		else if (!INCIDENTDETECTIONACTIVATED) {
			currentBottleNeck = bottleNeck;
			currentBottleNeckCapacity = ((QueueLink)currentBottleNeck).getSimulatedFlowCapacity() / SimulationTimer.getSimTickTime();

		}

		// get the array index of the bottleneck link
		int bottleNeckIndex = 0;
		for (int i = 0; i < routeLinks.length; i++) {
			if (currentBottleNeck.equals(routeLinks[i])) {
				bottleNeckIndex = i;
				break;
			}
		}
		
//		Agents after bottleneck drive free speed (bottle neck index + 1)
		for (int i = bottleNeckIndex + 1; i < routeLinks.length; i++) {
			ttFreeSpeedPart += this.ttFreeSpeeds.get(routeLinks[i].getId().toString());
		}

		
		if (DISTRIBUTIONCHECKACTIVATED) {
	
			for (int r = bottleNeckIndex; r >= 0; r--) {
				Link link = routeLinks[r];
				double linkAgents = this.numberOfAgents.get(link.getId().toString());
				double linkFreeSpeedTT = this.ttFreeSpeeds.get(link.getId().toString());
				
				if ( (linkAgents / currentBottleNeckCapacity) <= linkFreeSpeedTT ) {
					ttFreeSpeedPart += linkFreeSpeedTT;
					log.debug("Distribution check: Link " + link.getId().toString() + " is added to freeSpeedPart.");
				}
				else {
					int agentsUpToLink = 0;
					double freeSpeedUpToLink = 0;
					for (int p = 0; p <= r; p++ ) {
						agentsUpToLink += this.numberOfAgents.get(routeLinks[p].getId().toString());
						freeSpeedUpToLink += this.ttFreeSpeeds.get(routeLinks[p].getId().toString());
						ttFreeSpeedBeforeBottleNeck = freeSpeedUpToLink;
					}
					
					if (BACKGROUNDNOISECOMPENSATIONACTIVATED) {
						agentsUpToLink += getAdditionalAgents(route, r);
					}
					
					if ( (agentsUpToLink / currentBottleNeckCapacity) >= freeSpeedUpToLink ) {
						guidanceObjectWillQueue = true;
						currentBottleNeck = link;
						agentsToQueueAtBottleNeck = agentsUpToLink;
//						log.debug("Distribution check: Critical link. All agents on link " + criticalCongestedLink.getId().toString() + " will NOT pass the bottleneck before the guidance object arrive.");
						break;
					}				
					else {
						ttFreeSpeedPart += linkFreeSpeedTT;
//						log.debug("Distribution check: Non-critical link. All agents on link " + criticalCongestedLink.getId().toString() + " will pass the bottle neck when before the guidancde object arrive." );
					}
				}
			}
			if (guidanceObjectWillQueue) {
				log.debug("The guidance object will queue with agents ahead.");
			}
			else {
				log.debug("The guidance object will not queue at the bottleneck. No critical congested link was found.");
			}
//			log.debug("Distribution check performed: " + agentsToQueueAtBottleNeck + " will queue at link " + criticalCongestedLink.getId().toString());
		}
		
				
//		Run without distribution check
		else if (!DISTRIBUTIONCHECKACTIVATED){

			// count agents on congested part of the route 
			ttFreeSpeedBeforeBottleNeck = 0;
			for (int i = 0; i <= bottleNeckIndex; i++) {
				agentsToQueueAtBottleNeck += this.numberOfAgents.get(routeLinks[i].getId().toString());
				ttFreeSpeedBeforeBottleNeck += this.ttFreeSpeeds.get(routeLinks[i].getId().toString());
			}
			if (BACKGROUNDNOISECOMPENSATIONACTIVATED) {
				agentsToQueueAtBottleNeck += getAdditionalAgents(route, bottleNeckIndex);
			}
			log.debug("Distribution check inactivated: " + agentsToQueueAtBottleNeck + " agents before bottle neck link " + currentBottleNeck.getId().toString());
		}
		
		predictedTT = (agentsToQueueAtBottleNeck / currentBottleNeckCapacity) + ttFreeSpeedPart;
//		Check route criteria if distribution check is deactivated
		if ( !DISTRIBUTIONCHECKACTIVATED &&
				!(agentsToQueueAtBottleNeck / currentBottleNeckCapacity > ttFreeSpeedBeforeBottleNeck) ) {
			predictedTT = getFreeSpeedRouteTravelTime(route);
		}
	
			
		return predictedTT;
	}
	
	
	private int getAdditionalAgents(final Route route, final int linkIndex){
		double totalExtraAgents = 0.0;
		double netFlow = 0.0;
//		double distanceToBottleNeck = 0.0;

		//check distance and free speed travel time from start node to bottleneck
		Link [] routeLinks = route.getLinkRoute();
		String linkId1 = routeLinks[linkIndex].getId().toString();
		double ttToLink = ttFreeSpeedUpToAndIncludingLink.get(linkId1);
//		for (int i = 0; i <= linkIndex; i++) {
//			String linkId = routeLinks[linkIndex].getId().toString();
//			ttToLink += this.ttFreeSpeeds.get(linkId);
//		}
		
		
		List<Link> inAndOutLinks = new ArrayList<Link>();
		inAndOutLinks.addAll(this.getOutlinks(route));
		inAndOutLinks.addAll(this.getInlinks(route));
		Iterator<Link> it = inAndOutLinks.iterator();
		while (it.hasNext()) {
			Link link = it.next();
			String linkId = link.getId().toString();
			double extraAgents = 0.0;
			double flow = getInOutFlow(link, route);
			if ( this.ttFreeSpeedUpToAndIncludingLink.get(linkId) > ttToLink || this.ttFreeSpeedUpToAndIncludingLink == null) {
				extraAgents = 0.0;
			}
			else {
				extraAgents = flow * this.ttFreeSpeedUpToAndIncludingLink.get(linkId);
				System.out.println("Extra agents = " + flow + " * " + this.ttFreeSpeedUpToAndIncludingLink.get(linkId) + " (link" + linkId + " )." );
			}
			totalExtraAgents += extraAgents;
		}
		
		/*
		//Sum up INFLOWS and weigh it corresponding to their distance to the start node.
		Iterator<Link> itInlinks = this.getInlinks(route).iterator();
		while(itInlinks.hasNext()){
			Link inLink = itInlinks.next();
			double inFlow = getInOutFlow(inLink, route);
			double weightedInFlow = 0;
			//don't include Links after the bottleNeck. Also takes care of null-case
			if(this.inFlowDistances.get(inLink.getId().toString()) == null
					|| this.inFlowDistances.get(inLink.getId().toString()) > distanceToBottleNeck){
				weightedInFlow = 0;
			}
			else{
				weightedInFlow = inFlow * this.inFlowDistances.get(inLink.getId().toString());				}
			weightedNetFlow += weightedInFlow;
		}
		
		//Sum up OUTFLOWS and weigh it corresponding to their distance to the start node.
		Iterator<Link> itOutlinks = this.getOutlinks(route).iterator();
		while(itOutlinks.hasNext()){
			Link outLink = itOutlinks.next();
			double outFlow = getOutFlow(outLink, route);
			double weightedOutFlow = 0;
			if( this.outFlowDistances.get(outLink.getId().toString()) == null
					|| this.outFlowDistances.get(outLink.getId().toString()) > distanceToBottleNeck){
				weightedOutFlow = 0;
			}
			else{
				weightedOutFlow = outFlow * this.outFlowDistances.get(outLink.getId().toString());
			}
			weightedNetFlow -= weightedOutFlow;
		}
		*/
//		netFlow = weightedNetFlow / ttToLink;
//		System.out.println("Net flow = " + weightedNetFlow + " / " + ttToLink + " = " + netFlow);
		return (int)(totalExtraAgents);
	}
	
	
	/*
	//set new in and outflows and reset numbersPassedOnInAndOutLinks every [FLOWUPDATETIME]
	private void calculateInAndOutFlows() {
		//inLinksMainRoute
		Iterator<Link> itInlinksMain = inLinksMainRoute.iterator();
		while(itInlinksMain.hasNext()){
			Link inLink = itInlinksMain.next();
			double flow = (double)numbersPassedOnInAndOutLinks.get(inLink.getId().toString())/FLOWUPDATETIME;
			this.inFlows.put(inLink.getId().toString(), flow);
			numbersPassedOnInAndOutLinks.put(inLink.getId().toString(), 0);
		}

		//outLinksMainRoute
		Iterator<Link> itOutlinksMain = outLinksMainRoute.iterator();
		while(itOutlinksMain.hasNext()){
			Link outLink = itOutlinksMain.next();
			double flow = (double)numbersPassedOnInAndOutLinks.get(outLink.getId().toString())/FLOWUPDATETIME;
			this.outFlows.put(outLink.getId().toString(), flow);
			numbersPassedOnInAndOutLinks.put(outLink.getId().toString(), 0);
		}
		//inLinksAlternativeRoute
		Iterator<Link> itInlinksAlt = inLinksAlternativeRoute.iterator();
		while(itInlinksAlt.hasNext()){
			Link inLink = itInlinksAlt.next();
			double flow = (double)numbersPassedOnInAndOutLinks.get(inLink.getId().toString())/FLOWUPDATETIME;
			this.inFlows.put(inLink.getId().toString(), flow);
			numbersPassedOnInAndOutLinks.put(inLink.getId().toString(), 0);
		}

		//outLinksAlternativeRoute
		Iterator<Link> itOutlinksAlt = outLinksAlternativeRoute.iterator();
		while(itOutlinksAlt.hasNext()){
			Link outLink = itOutlinksAlt.next();
			double flow = (double)numbersPassedOnInAndOutLinks.get(outLink.getId().toString())/FLOWUPDATETIME;
			this.outFlows.put(outLink.getId().toString(), flow);
			numbersPassedOnInAndOutLinks.put(outLink.getId().toString(), 0);
		}		
	}
	*/
	
	
	private List<Link> getOutlinks(Route route) {
		if (route == this.mainRoute) {
			return this.outLinksMainRoute;
		}
		else {
			return this.outLinksAlternativeRoute;
		}
	}

	private List<Link> getInlinks(Route route) {
		if (route == this.mainRoute) {
			return this.inLinksMainRoute;
		}
		else {
			return this.inLinksAlternativeRoute;
		}
	}

/*
	private double getOutFlow(Link outLink, Route route) {
		double flow;
		if(route == this.mainRoute){
			flow = this.outFlows.get(outLink.getId().toString());
		}
		else if(route == this.alternativeRoute){
			flow = this.outFlows.get(outLink.getId().toString());
		}
		else{
			flow = 0;
			System.err.println("Something is wrong, this shouldn't happen!");
		}
		return flow;
	}
*/

	private double getInOutFlow(Link inLink, Route route) {
		double flow;
		String linkId = inLink.getId().toString();
		if(route == mainRoute){
			flow = this.extraFlowsMainRoute.get(linkId);
		}
		else if(route == alternativeRoute){
			flow = this.extraFlowsAlternativeRoute.get(linkId);
		}
		else{
			flow = 0;
			System.err.println("Something is wrong, this shouldn't happen!");
		}
		return flow;
	}
	
	
	public double getFlow(Link link) {
		double flow = this.intraFlows.get(link.getId().toString());
		return flow;
	}
	
	public double getCapacity(Link link) {
		double capacity = this.capacities.get(link.getId().toString());
		return capacity;
	}
	
	public Link getDetectedBottleNeck(final Route route){
		Link l;
		if(route == mainRoute){
			l = currentBottleNeckMainRoute;
		}
		else{
			l = currentBottleNeckAlternativeRoute;
		}
		return l;
	}
	
	public void setIncidentLink(final Link link, final Route route){
		if(route == mainRoute){
			currentBottleNeckMainRoute = link;
		}
		else{
			currentBottleNeckAlternativeRoute = link;
		}
	}
	
	private void setIncidentCapacity(Double currentBottleNeckCapacity, Route route) {
		if(route == mainRoute){
			currentBNCapacityMainRoute = currentBottleNeckCapacity;
		}
		else{
			currentBNCapacityAlternativeRoute = currentBottleNeckCapacity;
		}
	}
	
	private Double getIncidentCapacity(Route route) {
		double cap;
		if(route == mainRoute){
			cap = currentBNCapacityMainRoute;
		}
		else{
			cap = currentBNCapacityAlternativeRoute;
		}
		return cap;
	}
	
	private Link searchAccidentsOnRoutes(final String accidentLinkId) {
		Route r = this.mainRoute;
		for (int j = 0; j < 2; j++) {
			Link[] links = r.getLinkRoute();
			for (int i = 0; i < links.length; i++) {
				if (links[i].getId().toString().equalsIgnoreCase(accidentLinkId)) {
					return links[i];
				}
			}
			r = this.alternativeRoute;
		}
		throw new IllegalArgumentException("The set Accident has to be on one of the routes if using this implementation of ControlInput!");
	}
	
	public void setAccidents(final List<Accident> accidents) {
		this.accidents = accidents;
	}
}

