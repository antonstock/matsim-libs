package org.matsim.freight.carriers.consistency_checkers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import org.matsim.freight.carriers.*;

import java.util.*;

import static org.matsim.core.config.ConfigUtils.addOrGetModule;

	/**
	 *
	 *  @author antonstock
	 *	This class checks, if all shipments can be transported -> vehicle has to be large enough and in operation during pickup/delivery times.
	 *
	 */

public class VehicleScheduleTest {

	private static final Logger log = LogManager.getLogger(VehicleScheduleTest.class);

	public static boolean doTimeWindowsOverlap(TimeWindow tw1, TimeWindow tw2) {
		return tw1.getStart() <= tw2.getEnd() && tw2.getStart() <= tw1.getEnd();
	}

	public static boolean doesShipmentFitInVehicle(Double capacity, Double demand) {
		return demand <= capacity;
	}

	public record VehicleInfo(TimeWindow operationWindow, double capacity){}

	public record ShipmentPickupInfo(TimeWindow pickupWindow, double capacityDemand) {}

	public record ShipmentDeliveryInfo(TimeWindow deliveryWindow, double capacityDemand){}


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
		 *
		 * TODO: Ab hier Umzug zu CarrierConsistencyCheckers
		 *
		 */

		Map<String, VehicleInfo> vehicleOperationWindows = new HashMap<>();

		Map<String, ShipmentPickupInfo> shipmentPickupWindows = new HashMap<>();

		Map<String, ShipmentDeliveryInfo> shipmentDeliveryWindows = new HashMap<>();

		//determine the operating hours of all available vehicles
		for (Carrier carrier : carriers.getCarriers().values()) {
			for (CarrierVehicle carrierVehicle : carrier.getCarrierCapabilities().getCarrierVehicles().values()) {
				//read vehicle ID
				String vehicleID = carrierVehicle.getType().getId().toString();
				//read earliest start and end of vehicle in seconds after midnight (21600 = 06:00:00 (am), 64800 = 18:00:00
				var vehicleOperationStart = carrierVehicle.getEarliestStartTime();
				var vehicleOperationEnd = carrierVehicle.getLatestEndTime();
				var capacity = carrierVehicle.getType().getCapacity().getOther();
				//create TimeWindow with start and end of operation
				TimeWindow operationWindow = TimeWindow.newInstance(vehicleOperationStart, vehicleOperationEnd);
				//write vehicle ID (key as string) and time window of operation (value as TimeWindow) in HashMap
				vehicleOperationWindows.put(vehicleID, new VehicleInfo(operationWindow, capacity));
			}
		}

		//determine pickup hours of shipments
		//Shipment ID (key as string) and times (value as double) are being stored in HashMaps
		for (Carrier carrier : carriers.getCarriers().values()) {
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				String shipmentID = shipment.getId().toString();
				TimeWindow shipmentPickupWindow = shipment.getPickupStartingTimeWindow();
				var shipmentSize = shipment.getCapacityDemand();
				shipmentPickupWindows.put(shipmentID, new ShipmentPickupInfo(shipmentPickupWindow, shipmentSize));
			}
		}

		//determine delivery hours of shipments
		//Shipment ID (key as string) and times (value as double) are being stored in HashMaps
		for (Carrier carrier : carriers.getCarriers().values()) {
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				String shipmentID = shipment.getId().toString();
				TimeWindow shipmentDeliveryWindow = shipment.getDeliveryStartingTimeWindow();
				var shipmentSize = shipment.getCapacityDemand();
				shipmentDeliveryWindows.put(shipmentID, new ShipmentDeliveryInfo(shipmentDeliveryWindow, shipmentSize));
			}
		}

		Map<String, List<String>> validAssignments = new HashMap<>();
		Map<String, String> nonTransportableShipments = new HashMap<>();

		for (String shipmentID : shipmentPickupWindows.keySet()) {
			ShipmentPickupInfo pickupInfo = shipmentPickupWindows.get(shipmentID);
			ShipmentDeliveryInfo deliveryInfo = shipmentDeliveryWindows.get(shipmentID);
			boolean shipmentCanBeTransported = false;
			boolean capacityFits = false;
			boolean pickupOverlap = false;
			boolean deliveryOverlap = false;

			for (String vehicleID : vehicleOperationWindows.keySet()) {
				VehicleInfo vehicleInfo = vehicleOperationWindows.get(vehicleID);

				capacityFits = doesShipmentFitInVehicle(vehicleInfo.capacity(), pickupInfo.capacityDemand());

				pickupOverlap = doTimeWindowsOverlap(vehicleInfo.operationWindow(), pickupInfo.pickupWindow());

				deliveryOverlap = doTimeWindowsOverlap(vehicleInfo.operationWindow(), deliveryInfo.deliveryWindow());

				if (capacityFits && pickupOverlap && deliveryOverlap) {
					shipmentCanBeTransported = true;
					validAssignments.computeIfAbsent(vehicleID, k -> new ArrayList<>()).add(shipmentID);
				}
			}
			System.out.println(capacityFits+""+pickupOverlap+""+deliveryOverlap);

			if (!shipmentCanBeTransported) {
				if (!capacityFits) {
					nonTransportableShipments.put(shipmentID,"Vehicle(s) in operation is too small.");
				} else if (!pickupOverlap) {
					nonTransportableShipments.put(shipmentID,"No sufficient vehicle in operation");
				} else if (!deliveryOverlap) {
					nonTransportableShipments.put(shipmentID,"No sufficient vehicle in operation");
				}
			}
		}
		if (!nonTransportableShipments.isEmpty()) {
			log.warn("A total of '{}' shipment(s) cannot be transported. Affected shipment(s): '{}' Reason(s): '{}'",nonTransportableShipments.size(), nonTransportableShipments.keySet(),nonTransportableShipments.values());
		}
	}
}
