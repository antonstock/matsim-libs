/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2023 by the members listed in the COPYING,        *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */

package lsp;

import lsp.shipment.LSPShipment;
import lsp.shipment.ShipmentPlanElement;
import lsp.shipment.ShipmentUtils;
import lsp.usecase.TransshipmentHub;
import lsp.usecase.UsecaseUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.core.utils.misc.Time;
import org.matsim.utils.objectattributes.attributable.AttributesXmlReaderDelegate;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.xml.sax.Attributes;

import java.util.*;

import static lsp.LSPConstants.*;

/**
 * Reads data out of LSPPlans file and builds the LSPs with their according resources, shipments and plans.
 * StartTag mainly for parsing data, endTag for assigning data to according LSP.
 * @author nrichter (Niclas Richter)
 */
class LSPPlanXmlParserV1 extends MatsimXmlParser {

	public static final Logger logger = LogManager.getLogger( LSPPlanXmlParserV1.class );

	private LSP currentLsp = null;
	private Carrier currentCarrier = null;
	private LSPShipment currentShipment = null;
	private final LSPs lsPs;
	private final Carriers carriers;
	private CarrierCapabilities.Builder capabilityBuilder;
	private TransshipmentHub hubResource;
	private String currentHubId;
	private Double currentHubFixedCost;
	private String currentHubLocation;
	private String chainId;
	private Double score;
	private String selected;
	private String shipmentPlanId;
	private final Map<String, String> elementIdResourceIdMap = new LinkedHashMap<>();
	private final Map<String, ShipmentPlanElement> planElements = new LinkedHashMap<>();
	private final AttributesXmlReaderDelegate attributesReader = new AttributesXmlReaderDelegate();


	LSPPlanXmlParserV1( LSPs lsPs, Carriers carriers ) {
		super();
		this.lsPs = lsPs;
		this.carriers = carriers;
	}

	@Override
	public void startTag(String name, Attributes atts, Stack<String> context) {
		org.matsim.utils.objectattributes.attributable.Attributes currAttributes = new org.matsim.utils.objectattributes.attributable.AttributesImpl();
		switch (name) {
			case LSP -> {
				String lspId = atts.getValue(ID);
				Gbl.assertNotNull(lspId);
				currentLsp = LSPUtils.LSPBuilder.getInstance(Id.create(lspId, LSP.class))
						.setLogisticChainScheduler(UsecaseUtils.createDefaultSimpleForwardLogisticChainScheduler(Collections.emptyList()))
						.setInitialPlan(new LSPPlanImpl())
						.build();
			}
			case CARRIER -> {
				String carrierId = atts.getValue(ID);
				Gbl.assertNotNull(carrierId);
				currentCarrier = carriers.getCarriers().get(Id.create(carrierId, Carrier.class));
			}
			case HUB -> {
				currentHubId = atts.getValue(ID);
				Gbl.assertNotNull(currentHubId);
				currentHubLocation = atts.getValue(LOCATION);
				Gbl.assertNotNull(currentHubLocation);
				currentHubFixedCost = Double.parseDouble(atts.getValue(FIXED_COST));
				Gbl.assertNotNull(currentHubFixedCost);
			}
			case CAPABILITIES -> {
				String fleetSize = atts.getValue(FLEET_SIZE);
				Gbl.assertNotNull(fleetSize);
				this.capabilityBuilder = CarrierCapabilities.Builder.newInstance();
				if (fleetSize.toUpperCase().equals(CarrierCapabilities.FleetSize.FINITE.toString())) {
					this.capabilityBuilder.setFleetSize(CarrierCapabilities.FleetSize.FINITE);
				} else {
					this.capabilityBuilder.setFleetSize(CarrierCapabilities.FleetSize.INFINITE);
				}
			}
			case SCHEDULER -> {
				double capacityNeedFixed = Double.parseDouble(atts.getValue(CAPACITY_NEED_FIXED));
				double capacityNeedLinear = Double.parseDouble(atts.getValue(CAPACITY_NEED_LINEAR));
				hubResource = UsecaseUtils.TransshipmentHubBuilder.newInstance(Id.create(currentHubId, LSPResource.class), Id.createLinkId(currentHubLocation), null)
						.setTransshipmentHubScheduler(UsecaseUtils.TranshipmentHubSchedulerBuilder.newInstance()
								.setCapacityNeedFixed(capacityNeedFixed) //Time needed, fixed (for Scheduler)
								.setCapacityNeedLinear(capacityNeedLinear) //additional time needed per shipmentSize (for Scheduler)
								.build())
						.build();
			}
			case ATTRIBUTES -> {
				switch (context.peek()) {
					case SHIPMENT -> currAttributes = currentShipment.getAttributes();
					case LSP -> currAttributes = currentLsp.getAttributes();
					default ->
							throw new RuntimeException("could not derive context for attributes. context=" + context.peek());
				}
				attributesReader.startTag(name, atts, context, currAttributes);
			}
			case ATTRIBUTE -> {
				currAttributes = currentCarrier.getAttributes();
				Gbl.assertNotNull(currAttributes);
				attributesReader.startTag(name, atts, context, currAttributes);
			}
			case VEHICLE -> {
				String vehicleId = atts.getValue(ID);
				Gbl.assertNotNull(vehicleId);

				String depotLinkId = atts.getValue(DEPOT_LINK_ID);
				Gbl.assertNotNull(depotLinkId);

				String typeId = atts.getValue(TYPE_ID);
				Gbl.assertNotNull(typeId);
				VehicleType vehicleType = VehicleUtils.createVehicleType(Id.create(typeId, VehicleType.class));
				Gbl.assertNotNull(vehicleType);

				CarrierVehicle.Builder vehicleBuilder = CarrierVehicle.Builder.newInstance(Id.create(vehicleId, Vehicle.class), Id.create(depotLinkId, Link.class), vehicleType);
				String startTime = atts.getValue(VEHICLE_EARLIEST_START);
				if (startTime != null) vehicleBuilder.setEarliestStart(parseTimeToDouble(startTime));
				String endTime = atts.getValue(VEHICLE_LATEST_END);
				if (endTime != null) vehicleBuilder.setLatestEnd(parseTimeToDouble(endTime));

				CarrierVehicle vehicle = vehicleBuilder.build();
				capabilityBuilder.addVehicle(vehicle);
			}
			case SHIPMENT -> {
				String shipmentId = atts.getValue(ID);
				Gbl.assertNotNull(shipmentId);
				Id<LSPShipment> id = Id.create(shipmentId, LSPShipment.class);

				String from = atts.getValue(FROM);
				Gbl.assertNotNull(from);
				String to = atts.getValue(TO);
				Gbl.assertNotNull(to);
				String sizeString = atts.getValue(SIZE);
				Gbl.assertNotNull(sizeString);
				int size = Integer.parseInt(sizeString);
				ShipmentUtils.LSPShipmentBuilder shipmentBuilder = ShipmentUtils.LSPShipmentBuilder.newInstance(id);

				shipmentBuilder.setFromLinkId(Id.createLinkId(from));
				shipmentBuilder.setToLinkId(Id.createLinkId(to));
				shipmentBuilder.setCapacityDemand(size);

				String startPickup = atts.getValue(START_PICKUP);
				String endPickup = atts.getValue(END_PICKUP);
				String startDelivery = atts.getValue(START_DELIVERY);
				String endDelivery = atts.getValue(END_DELIVERY);
				String pickupServiceTime = atts.getValue(PICKUP_SERVICE_TIME);
				String deliveryServiceTime = atts.getValue(DELIVERY_SERVICE_TIME);

				if (startPickup != null && endPickup != null)
					shipmentBuilder.setStartTimeWindow(TimeWindow.newInstance(parseTimeToDouble(startPickup), parseTimeToDouble(endPickup)));
				if (startDelivery != null && endDelivery != null)
					shipmentBuilder.setEndTimeWindow(TimeWindow.newInstance(parseTimeToDouble(startDelivery), parseTimeToDouble(endDelivery)));
				if (pickupServiceTime != null)
					shipmentBuilder.setPickupServiceTime(parseTimeToDouble(pickupServiceTime));
				if (deliveryServiceTime != null)
					shipmentBuilder.setDeliveryServiceTime(parseTimeToDouble(deliveryServiceTime));

				currentShipment = shipmentBuilder.build();
				currentLsp.getShipments().add(currentShipment);
			}
			case PLAN -> {
				score = Double.valueOf(atts.getValue(SCORE));
				Gbl.assertNotNull(score);
				selected = atts.getValue(SELECTED);
				Gbl.assertNotNull(selected);
			}
			case LOGISTIC_CHAIN -> {
				chainId = atts.getValue(ID);
				Gbl.assertNotNull(chainId);
			}
			case RESOURCES -> {
			}
			case LOGISTIC_CHAIN_ELEMENT -> {
				String logisticChainElementId = atts.getValue(ID);
				String resourceId = atts.getValue(RESOURCE_ID);

				elementIdResourceIdMap.put(logisticChainElementId, resourceId);
			}
			case SHIPMENT_PLAN -> {
				shipmentPlanId = atts.getValue(SHIPMENT_ID);
				Gbl.assertNotNull(shipmentPlanId);
			}
			case ELEMENT -> {
				String elementId = atts.getValue(ID);
				Gbl.assertNotNull(elementId);

				String type = atts.getValue(TYPE);
				Gbl.assertNotNull(type);

				String startTime = atts.getValue(START_TIME);
				Gbl.assertNotNull(startTime);

				String endTime = atts.getValue(END_TIME);
				Gbl.assertNotNull(endTime);

				String resourceId = atts.getValue(RESOURCE_ID);
				Gbl.assertNotNull(resourceId);

				ShipmentPlanElement planElement = null;

				switch (type) {
					case "LOAD" -> {
						var planElementBuilder = ShipmentUtils.ScheduledShipmentLoadBuilder.newInstance();
						planElementBuilder.setStartTime(parseTimeToDouble(startTime));
						planElementBuilder.setEndTime(parseTimeToDouble(endTime));
						planElementBuilder.setResourceId(Id.create(resourceId, LSPResource.class));
						planElement = planElementBuilder.build();
					}
					case "TRANSPORT" -> {
						var planElementBuilder = ShipmentUtils.ScheduledShipmentTransportBuilder.newInstance();
						planElementBuilder.setStartTime(parseTimeToDouble(startTime));
						planElementBuilder.setEndTime(parseTimeToDouble(endTime));
						planElementBuilder.setResourceId(Id.create(resourceId, LSPResource.class));
						planElement = planElementBuilder.build();
					}
					case "UNLOAD" -> {
						var planElementBuilder = ShipmentUtils.ScheduledShipmentUnloadBuilder.newInstance();
						planElementBuilder.setStartTime(parseTimeToDouble(startTime));
						planElementBuilder.setEndTime(parseTimeToDouble(endTime));
						planElementBuilder.setResourceId(Id.create(resourceId, LSPResource.class));
						planElement = planElementBuilder.build();
					}
					case "HANDLE" -> {
						var planElementBuilder = ShipmentUtils.ScheduledShipmentHandleBuilder.newInstance();
						planElementBuilder.setStartTime(parseTimeToDouble(startTime));
						planElementBuilder.setEndTime(parseTimeToDouble(endTime));
						planElementBuilder.setResourceId(Id.create(resourceId, LSPResource.class));
						planElement = planElementBuilder.build();
					}
				}
				planElements.put(elementId, planElement);
			}
		}
	}


	@Override
	public void endTag(String name, String content, Stack<String> context) {
		switch (name) {
			case LSP -> {
				Gbl.assertNotNull(currentLsp);
				Gbl.assertNotNull(lsPs);
				Gbl.assertNotNull(lsPs.getLSPs());
				currentLsp.getPlans().remove(0); // empty plan zero was set for initialization of currentLSP
				lsPs.getLSPs().put(currentLsp.getId(), currentLsp);
				currentLsp = null;
			}
			case CARRIER -> {
				Gbl.assertNotNull(currentCarrier);
				Gbl.assertNotNull(carriers);
				Gbl.assertNotNull(carriers.getCarriers());
				LSPResource lspResource;

				switch (UsecaseUtils.getCarrierType(currentCarrier)) {
					case collectionCarrier -> lspResource = UsecaseUtils.CollectionCarrierResourceBuilder.newInstance(currentCarrier, null)
							.setCollectionScheduler(UsecaseUtils.createDefaultCollectionCarrierScheduler())
							.build();
					case mainRunCarrier -> lspResource = UsecaseUtils.MainRunCarrierResourceBuilder.newInstance(Id.create(currentCarrier.getId(), LSPResource.class), null)
							.setCarrier(currentCarrier)
							.setMainRunCarrierScheduler(UsecaseUtils.createDefaultMainRunCarrierScheduler())
							.build();
					case distributionCarrier -> lspResource = UsecaseUtils.DistributionCarrierResourceBuilder.newInstance(currentCarrier, null)
							.setDistributionScheduler(UsecaseUtils.createDefaultDistributionCarrierScheduler())
							.build();
					default -> throw new IllegalStateException("Unexpected value: " + currentCarrier.getAttributes().toString());
				}
				Gbl.assertNotNull(lspResource);
				currentLsp.getResources().add(lspResource);
				currentCarrier = null;
			}
			case HUB -> {
				currentLsp.getResources().add(hubResource);
				LSPUtils.setFixedCost(hubResource, currentHubFixedCost);
				hubResource = null;
				currentHubFixedCost = null;
				currentHubLocation = null;
				currentHubId = null;
			}
			case CAPABILITIES -> currentCarrier.setCarrierCapabilities(capabilityBuilder.build());
			case ATTRIBUTE -> attributesReader.endTag(name, content, context);
			case SHIPMENT -> this.currentShipment = null;
			case PLAN -> {
				LSPResource resource;
				List<LogisticChainElement> logisticChainElements = new LinkedList<>();

				for (Map.Entry<String, String> entry : elementIdResourceIdMap.entrySet()) {
					for (LSPResource currentResource : currentLsp.getResources()) {
						if (currentResource.getId().toString().equals(entry.getValue())) {
							resource = currentResource;
							Gbl.assertNotNull(resource);
							LogisticChainElement logisticChainElement = LSPUtils.LogisticChainElementBuilder.newInstance(Id.create(entry.getKey(), LogisticChainElement.class))
									.setResource(resource)
									.build();
							logisticChainElements.add(logisticChainElement);
						}
					}
				}

				elementIdResourceIdMap.clear();

				//create logisticChain
				LogisticChain logisticChain = LSPUtils.LogisticChainBuilder.newInstance(Id.create(chainId, LogisticChain.class))
						.addLogisticChainElement(logisticChainElements.get(0))
						.build();

				for (int i = 1; i < logisticChainElements.size(); i++) { // element 0 was already added in Builder as first element.
					logisticChainElements.get(i - 1).connectWithNextElement(logisticChainElements.get(i));
					logisticChain.getLogisticChainElements().add(logisticChainElements.get(i));
				}

				for (LSPShipment lspShipment : currentLsp.getShipments()) {
					logisticChain.getShipments().add(lspShipment);
				}

				final ShipmentAssigner singleSolutionShipmentAssigner = UsecaseUtils.createSingleLogisticChainShipmentAssigner();

				//create LspPlan
				LSPPlan currentPlan = LSPUtils.createLSPPlan()
						.addLogisticChain(logisticChain)
						.setAssigner(singleSolutionShipmentAssigner);


				currentPlan.setScore(score);
				currentPlan.setLSP(currentLsp);
				if (selected.equals("true")) {
					currentLsp.setSelectedPlan(currentPlan);
				} else {
					currentLsp.addPlan(currentPlan);
				}

			}
			case SHIPMENT_PLAN -> {
				for (LSPShipment shipment : currentLsp.getShipments()) {
					if (shipment.getId().toString().equals(shipmentPlanId)) {
						for (Map.Entry<String, ShipmentPlanElement> planElement : planElements.entrySet()) {
							shipment.getShipmentPlan().addPlanElement(Id.create(planElement.getKey(), ShipmentPlanElement.class), planElement.getValue());
						}
					}
				}
				shipmentPlanId = null;
				planElements.clear();
			}
		}
	}

	private double parseTimeToDouble (String timeString){
		if (timeString.contains(":")) {
			return Time.parseTime(timeString);
		} else {
			return Double.parseDouble(timeString);
		}
	}

}
