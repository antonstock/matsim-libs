package org.matsim.freight.carriers.consistency_checkers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.locationtech.jts.util.Assert;
import org.junit.jupiter.api.Assertions;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.freight.carriers.*;
import org.matsim.testcases.MatsimTestUtils;

import static org.matsim.core.config.ConfigUtils.addOrGetModule;

/**
 *
 *  @author antonstock
 *	VehicleCapacityTest checks, if all carriers have at least one vehicle with sufficient capacity for every job. If the capacity demand of a job
 * 	 * is higher than the highest vehicle capacity, capacityCheck will return false and a log warning with details about the affected carrier(s) and job(s).
 *
 * 	 */
public class VehicleCapacityTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	/**
	 * This test will check if the vehicles of carriers c1 and c2 have enough capacity to handle the given SHIPMENTS. This test should return TRUE.
	 */
	@Test
	void testVehicleCapacityShipments_passes() {

		Config config = ConfigUtils.createConfig();

		FreightCarriersConfigGroup freightConfigGroup = addOrGetModule(config, FreightCarriersConfigGroup.class);
		freightConfigGroup.setCarriersFile(utils.getPackageInputDirectory() + "CCTestCarriersShipmentsPASS.xml");
		freightConfigGroup.setCarriersVehicleTypesFile(utils.getPackageInputDirectory() + "CCTestVeh.xml");

		Scenario scenario = ScenarioUtils.loadScenario(config);

		//load carriers according to freight config
		CarriersUtils.loadCarriersAccordingToFreightConfig( scenario );

		Carriers carriers = CarriersUtils.getCarriers(scenario);

		boolean areCarriersCapable = CarrierConsistencyCheckers.capacityCheck(carriers);
		Assertions.assertTrue(areCarriersCapable, "At least one vehicle of every carrier has enough capacity for the largest job.");

	}

	/**
	 * This test will check if the vehicles of carriers c1 and c2 have enough capacity to handle the given jobs. This test should return FALSE.
	 * ccCarrierWithShipments1: shipment "large_shipment" capacity demand of 33 is too high
	 * ccCarrierWithShipments2: shipment "large_shipment" capacity demand of 16 is too high
	 */
	@Test
	void testVehicleCapacityShipments_failes() {

		Config config = ConfigUtils.createConfig();

		FreightCarriersConfigGroup freightConfigGroup = addOrGetModule(config, FreightCarriersConfigGroup.class);
		freightConfigGroup.setCarriersFile(utils.getPackageInputDirectory() + "CCTestCarriersShipmentsFAIL.xml");
		freightConfigGroup.setCarriersVehicleTypesFile(utils.getPackageInputDirectory() + "CCTestVeh.xml");

		Scenario scenario = ScenarioUtils.loadScenario(config);

		//load carriers according to freight config
		CarriersUtils.loadCarriersAccordingToFreightConfig( scenario );

		Carriers carriers = CarriersUtils.getCarriers(scenario);

		boolean areCarriersCapable = CarrierConsistencyCheckers.capacityCheck(carriers);
		//TODO: @KMT: Assert.isFalse kann leider nicht gefunden werden, ich habe aber in der Bibliothek von junit "Assertions.assertFalse" gefunden, kann/darf ich das auch benutzen?
		// @Anton: Jo, passt. Habe das oben auch angepasst auf Assertions.
		Assertions.assertFalse(areCarriersCapable, "At least one shipment's capacity demand is too high.");
		//@Todo @Anton: Die Fehlermeldungen in der Assertion sind so gemeint, dass diese ausgegeben werden, wenn die Überprüfung fehlschlägt.
		// d.h., du müsstest mMn jeweils den Text umdrehen. --> Bitte auch bei den anderen Tests schauen.
		// (Kannst du auch ausprobieren, wenn du mal den Test umdrehst oder den Input vertauschst ;) )
	}

	/**
	 * This test will check if the vehicles of carriers c1 and c2 have enough capacity to handle the given SERVICES. This test should return TRUE.
	 */
	@Test
	void testVehicleCapacityServices_passes() {

		Config config = ConfigUtils.createConfig();

		FreightCarriersConfigGroup freightConfigGroup = addOrGetModule(config, FreightCarriersConfigGroup.class);
		freightConfigGroup.setCarriersFile(utils.getPackageInputDirectory() + "CCTestCarriersServicesPASS.xml");
		freightConfigGroup.setCarriersVehicleTypesFile(utils.getPackageInputDirectory() + "CCTestVeh.xml");

		Scenario scenario = ScenarioUtils.loadScenario(config);

		//load carriers according to freight config
		CarriersUtils.loadCarriersAccordingToFreightConfig( scenario );

		Carriers carriers = CarriersUtils.getCarriers(scenario);

		boolean areCarriersCapable = CarrierConsistencyCheckers.capacityCheck(carriers);
		Assert.isTrue(areCarriersCapable, "At least one vehicle of every carrier has enough capacity for the largest job.");

	}

	/**
	 * This test will check if the vehicles of carriers c1 and c2 have enough capacity to handle the given jobs. This test should return FALSE.
	 * ccCarrierWithServices1: service "extra_large_service" capacity demand of 31 is too high
	 * ccCarrierWithServices2: service "extra_large_service" capacity demand of 33 is too high
	 */
	@Test
	void testVehicleCapacityServices_fails() {

		Config config = ConfigUtils.createConfig();

		FreightCarriersConfigGroup freightConfigGroup = addOrGetModule(config, FreightCarriersConfigGroup.class);
		freightConfigGroup.setCarriersFile(utils.getPackageInputDirectory() + "CCTestCarriersServicesFAIL.xml");
		freightConfigGroup.setCarriersVehicleTypesFile(utils.getPackageInputDirectory() + "CCTestVeh.xml");

		Scenario scenario = ScenarioUtils.loadScenario(config);

		//load carriers according to freight config
		CarriersUtils.loadCarriersAccordingToFreightConfig( scenario );

		Carriers carriers = CarriersUtils.getCarriers(scenario);

		boolean areCarriersCapable = CarrierConsistencyCheckers.capacityCheck(carriers);
		Assertions.assertFalse(areCarriersCapable, "At least one shipment's capacity demand is too high.");
	}
}

