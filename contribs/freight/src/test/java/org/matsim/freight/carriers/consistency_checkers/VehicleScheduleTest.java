package org.matsim.freight.carriers.consistency_checkers;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.freight.carriers.*;

import java.util.*;

import static org.matsim.core.config.ConfigUtils.addOrGetModule;

	/**
	 *
	 *  @author antonstock
	 *	This class will check if the given vehicles are in operation while the shipments have to be collected, shipped and/or droped.
	 *
	 */

public class VehicleScheduleTest {

	//if function returns true: given time windows overlap
	public static boolean doTimeWindowsOverlap(TimeWindow tw1, TimeWindow tw2) {
		//System.out.println("Do time windows overlap: " + tw1 + " and " + tw2);
		//System.out.println(tw1.getStart() <= tw2.getEnd() && tw2.getStart() <= tw1.getEnd());
		return tw1.getStart() <= tw2.getEnd() && tw2.getStart() <= tw1.getEnd();
	}

	public static void main(String[] args) {

		// relative path to Freight/Scenarios/CCTestInput/
		String pathToInput = "contribs/freight/scenarios/CCTestInput/";
		//names of xml-files
		String carriersXML = "CCTestCarriers.xml";
		String vehicleXML = "CCTestVeh.xml";

		Config config = ConfigUtils.createConfig();

		FreightCarriersConfigGroup freightConfigGroup;
		freightConfigGroup = addOrGetModule(config, FreightCarriersConfigGroup.class);

		freightConfigGroup.setCarriersFile(pathToInput + carriersXML);
		freightConfigGroup.setCarriersVehicleTypesFile(pathToInput + vehicleXML);

		Scenario scenario = ScenarioUtils.loadScenario(config);

		//load carriers according to freight config
		CarriersUtils.loadCarriersAccordingToFreightConfig(scenario);

		Carriers carriers = CarriersUtils.getCarriers(scenario);
		/**
		 * System.out.println("Starting 'IsVehicleBigEnoughTest'...");
		 */


		Map<String, TimeWindow> vehicleOperationWindows = new HashMap<>();

		Map<String, TimeWindow> shipmentPickupWindows = new HashMap<>();

		Map<String, TimeWindow> shipmentDeliveryWindows = new HashMap<>();

		//determine the operating hours of all available vehicles
		for (Carrier carrier : carriers.getCarriers().values()) {
			for (CarrierVehicle carrierVehicle : carrier.getCarrierCapabilities().getCarrierVehicles().values()) {
				//read vehicle ID
				String vehicleID = carrierVehicle.getType().getId().toString();
				//read earliest start and end of vehicle in seconds after midnight (21600 = 06:00:00 (am), 64800 = 18:00:00
				var vehicleOperationStart = carrierVehicle.getEarliestStartTime();
				var vehicleOperationEnd = carrierVehicle.getLatestEndTime();
				//create TimeWindow with start and end of operation
				TimeWindow operationWindow = TimeWindow.newInstance(vehicleOperationStart, vehicleOperationEnd);
				//write vehicle ID (key as string) and time window of operation (value as TimeWindow) in HashMap
				vehicleOperationWindows.put(vehicleID, operationWindow);
			}
		}

		//determine pickup hours of shipments
		//Shipment ID (key as string) and times (value as double) are being stored in HashMaps
		for (Carrier carrier : carriers.getCarriers().values()) {
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				String shipmentID = shipment.getId().toString();
				TimeWindow shipmentPickupWindow = shipment.getPickupStartingTimeWindow();
				shipmentPickupWindows.put(shipmentID, shipmentPickupWindow);
			}
		}
		//System.out.println("Pickup Times HashMap"+shipmentPickupWindows);
		//determine delivery hours of shipments
		//Shipment ID (key as string) and times (value as double) are being stored in HashMaps
		for (Carrier carrier : carriers.getCarriers().values()) {
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				String shipmentID = shipment.getId().toString();
				TimeWindow shipmentDeliveryWindow = shipment.getDeliveryStartingTimeWindow();
				shipmentDeliveryWindows.put(shipmentID, shipmentDeliveryWindow);
			}
		}
		//System.out.println("Delivery Times Hashmap"+shipmentDeliveryWindows);


		//check if operating hours of vehicles overlap with pickup hours of shipments
		Iterator<Map.Entry<String, TimeWindow>> iteratorP = shipmentPickupWindows.entrySet().iterator();
		//iteration trough whole HashMap
		while (iteratorP.hasNext()) {
			Map.Entry<String, TimeWindow> shipmentEntry = iteratorP.next();
			TimeWindow shipmentTimeWindow = shipmentEntry.getValue();
			//use doTimeWindowsOverlap function (see above)
			boolean isOverlaping = vehicleOperationWindows.values().stream().anyMatch(vehicleTimeWindow -> doTimeWindowsOverlap(vehicleTimeWindow, shipmentTimeWindow));
			//if windows overlap, shipment is covered and can be removed from map
			if (isOverlaping) {
				iteratorP.remove();
			}
		}
		//check if operating hours of vehicles overlap with delivery hours of shipments
		Iterator<Map.Entry<String, TimeWindow>> iteratorD = shipmentDeliveryWindows.entrySet().iterator();
		//iteration trough whole HashMap
		while (iteratorD.hasNext()) {
			Map.Entry<String, TimeWindow> shipmentEntry = iteratorD.next();
			TimeWindow shipmentTimeWindow = shipmentEntry.getValue();
			//use doTimeWindowsOverlap function (see above)
			boolean isOverlaping = vehicleOperationWindows.values().stream().anyMatch(vehicleTimeWindow -> doTimeWindowsOverlap(vehicleTimeWindow, shipmentTimeWindow));
			//if windows overlap, shipment is covered and can be removed from map
			if (isOverlaping) {
				iteratorD.remove();
			}
		}
		System.out.println("WARNING! At least one shipment cannot be picked up (Pick up windows while no vehicle is in operation). Affected shipment(s): "+shipmentPickupWindows.keySet());
		System.out.println("WARNING! At least one shipment cannot be delivered (Delivery windows while no vehicle is in operation). Affected shipment(s): "+shipmentDeliveryWindows.keySet());
	}
}

