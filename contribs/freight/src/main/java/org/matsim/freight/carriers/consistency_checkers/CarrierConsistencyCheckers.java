package org.matsim.freight.carriers.consistency_checkers;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.api.core.v01.Id;
import org.matsim.freight.carriers.*;
import org.matsim.vehicles.Vehicle;

import java.util.*;

public class CarrierConsistencyCheckers {
	public enum CheckResult {
		CHECK_SUCCESSFUL, CHECK_FAILED, ERROR
	}
	public enum AllJobsInToursDetailedCheckResult {
		ALL_JOBS_IN_TOURS, NOT_ALL_JOBS_IN_TOURS, JOBS_SCHEDULED_MULTIPLE_TIMES, JOBS_MISSING_AND_OTHERS_MULTIPLE_TIMES_SCHEDULED, JOBS_IN_TOUR_BUT_NOT_LISTED, CHECK_SUCCESSFUL, CHECK_FAILED, ERROR
	}

	private static final Logger log = LogManager.getLogger(CarrierConsistencyCheckers.class);

	private static Level currentLevel;

	public static void setLogLevel(Level level) {
		currentLevel = level;
		Configurator.setLevel(log.getName(), level);
	}
	private static void logMessage(String msg, Object... params) {
		if (currentLevel==Level.WARN){
			log.warn(msg, params);
		} else if (currentLevel==Level.ERROR) {
			log.error(msg, params);
		} else {
			log.info(msg, params);
		}
	}

	private static boolean doesJobFitInVehicle(Double capacity, Double demand) {
		return demand <= capacity;
	}
	private static boolean doTimeWindowsOverlap(TimeWindow tw1, TimeWindow tw2) {
		return tw1.getStart() <= tw2.getEnd() && tw2.getStart() <= tw1.getEnd();
	}
	private record VehicleInfo(TimeWindow operationWindow, double capacity) {
	}
	private record ShipmentPickupInfo(TimeWindow pickupWindow, double capacityDemand) {
	}
	private record ShipmentDeliveryInfo(TimeWindow deliveryWindow, double capacityDemand) {
	}
	private record ServiceInfo(TimeWindow serviceWindow, double capacityDemand) {
	}
	//Fixme :)
		//	public Result checkBefore(Level level) {
		//		boolean habeFehlergefunden = false;  //oder per int=0 und je True hochzählen...
		//		habeFehlergefunden =  capacityCheck().. (boolean zurückgeben (nur TRUE!!!))
		//		andere...
	//	}

	public static CheckResult checkBefore(Carriers carriers, Level lvl) {
		setLogLevel(lvl);
		int checkFailed = 0;
		if (capacityCheck(carriers, lvl)==CheckResult.CHECK_FAILED) {
			checkFailed++;
		}
		if (vehicleScheduleTest(carriers, lvl)==CheckResult.CHECK_FAILED) {
			checkFailed++;
		}
		if (checkFailed==0) {
			return CheckResult.CHECK_SUCCESSFUL;
		} else {
			return CheckResult.CHECK_FAILED;
		}
	}

	/**
	 * @author antonstock
	 * This method checks if every carrier is able to handle every given job (services + shipments) with the available fleet. This method does not check the vehicle's schedule but the capacity only.
	 * capacityCheck returns boolean isVehicleSufficient:
	 * = true: the highest vehicle capacity is greater or equal to the highest capacity demand
	 * = false: the highest vehicle capacity is less tan or equal to the highest capacity demand
	 */
	/*package-private*/ static CheckResult capacityCheck(Carriers carriers, Level lvl) {
	setLogLevel(lvl);
		//this map stores all checked carrier's IDs along with the result. true = carrier can handle all jobs.
		Map<Id<Carrier>, Boolean> isCarrierCapable = new HashMap<>();

		//go through all carriers after one another
		for (Carrier carrier : carriers.getCarriers().values()) {
			//List with all vehicle capacities of the current carrier
			List<Double> vehicleCapacityList = new ArrayList<>();
			//List with all jobs with a higher capacity demand than the largest vehicle's capacity
			List<CarrierJob> jobTooBigForVehicle = new LinkedList<>();
			//iterates through all vehicles of the current carrier and determines the vehicle capacities
			for (CarrierVehicle carrierVehicle : carrier.getCarrierCapabilities().getCarrierVehicles().values()) {
				vehicleCapacityList.add(carrierVehicle.getType().getCapacity().getOther());
			}
			//determine the highest capacity
			final double maxVehicleCapacity = Collections.max(vehicleCapacityList);

			//a carrier has only one job type: shipments OR services
			//checks if the largest vehicle is sufficient for all shipments
			//if not, shipment is added to the jobTooBigForVehicle-List.
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				if (shipment.getCapacityDemand() > maxVehicleCapacity) {
					jobTooBigForVehicle.add(shipment);
				}
			}
			//checks if the largest vehicle is sufficient for all services
			//if not, service is added to the jobTooBigForVehicle-List.
			for (CarrierService service : carrier.getServices().values()) {
				if (service.getCapacityDemand() > maxVehicleCapacity) {
					jobTooBigForVehicle.add(service);
				}
			}

			//if map is empty, there is a sufficient vehicle for every job
			if (jobTooBigForVehicle.isEmpty()) {
				logMessage("Carrier '{}': At least one vehicle has sufficient capacity ({}) for all jobs.", carrier.getId().toString(), maxVehicleCapacity);
				isCarrierCapable.put(carrier.getId(), true);
			} else {
				//if map is not empty, at least one job's capacity demand is too high for the largest vehicle.
				isCarrierCapable.put(carrier.getId(), false);
				logMessage("Carrier '{}': Demand of {} job(s) too high!", carrier.getId().toString(), jobTooBigForVehicle.size());
				for (CarrierJob job : jobTooBigForVehicle) {
					logMessage("Demand of Job '{}' is too high: '{}'", job.getId().toString(), job.getCapacityDemand());
				}
			}
		}
		//if every carrier has at least one vehicle with sufficient capacity for all jobs, return CAPACITY_SUFFICIENT
		if (isCarrierCapable.values().stream().allMatch(v -> v)) {
			return CheckResult.CHECK_SUCCESSFUL;
		} else {
			return CheckResult.CHECK_FAILED;
		}
	}

	/**
	 * this method will check if all existing carriers have vehicles with enough capacity in operation to handle all given jobs.
	 */

	public static CheckResult vehicleScheduleTest(Carriers carriers, Level lvl) {
	setLogLevel(lvl);
		//isCarrierCapable saves carrierIDs and check result (true/false)
		Map<Id<Carrier>, Boolean> isCarrierCapable = new HashMap<>();
		//go through all carriers
		for (Carrier carrier : carriers.getCarriers().values()) {
			//vehicleOperationWindows saves vehicle's ID along with its operation hours
			Map<Id<Vehicle>, VehicleInfo> vehicleOperationWindows = new HashMap<>();
			//these three maps save the job's ID along with its time window (pickup, delivery, service)
			Map<Id<? extends CarrierJob>, ShipmentPickupInfo> shipmentPickupWindows = new HashMap<>();
			Map<Id<? extends CarrierJob>, ShipmentDeliveryInfo> shipmentDeliveryWindows = new HashMap<>();
			Map<Id<? extends CarrierJob>, ServiceInfo> serviceWindows = new HashMap<>();
			//nonFeasibleJob saves Job ID and reason why a job can not be handled by a carrier -> not enough capacity at all (=job is too big for all existing vehicles) OR no sufficient vehicle in operation
			Map<Id<? extends CarrierJob>, String> nonFeasibleJob = new HashMap<>();
			//go through all vehicles of the current carrier and determine vehicle ID, operation time window & capacity
			for (CarrierVehicle carrierVehicle : carrier.getCarrierCapabilities().getCarrierVehicles().values()) {
				//read vehicle ID
				Id<Vehicle> vehicleID = carrierVehicle.getId();
				//get the start and end times of vehicle
				var vehicleOperationStart = carrierVehicle.getEarliestStartTime();
				var vehicleOperationEnd = carrierVehicle.getLatestEndTime();
				//get vehicle capacity
				var vehicleCapacity = carrierVehicle.getType().getCapacity().getOther();
				//create TimeWindow with start and end of operation
				TimeWindow operationWindow = TimeWindow.newInstance(vehicleOperationStart, vehicleOperationEnd);
				//write in Map: vehicle ID and Map VehicleInfo (time window of operation & capacity)
				vehicleOperationWindows.put(vehicleID, new VehicleInfo(operationWindow, vehicleCapacity));
			}
			/*
			 * SHIPMENTS PART
			 */
			//collects information about all existing shipments: IDs, times for pickup, capacity demand
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				shipmentPickupWindows.put(shipment.getId(), new ShipmentPickupInfo(shipment.getPickupStartingTimeWindow(), shipment.getCapacityDemand()));
				shipmentDeliveryWindows.put(shipment.getId(), new ShipmentDeliveryInfo(shipment.getDeliveryStartingTimeWindow(), shipment.getCapacityDemand()));
			}

			//run through all existing shipments
			for (Id<? extends CarrierJob> shipmentID : shipmentPickupWindows.keySet()) {
				//determine pickup time window
				ShipmentPickupInfo pickupInfo = shipmentPickupWindows.get(shipmentID);
				//determine delivery time window
				ShipmentDeliveryInfo deliveryInfo = shipmentDeliveryWindows.get(shipmentID);

				//isTransportable will be true if the current shipment can be transported by at least one vehicle
				boolean isTransportable = false;
				boolean capacitySufficient;
				boolean pickupOverlap;
				boolean deliveryOverlap;

				//runs through all vehicles
				for (Id<Vehicle> vehicleID : vehicleOperationWindows.keySet()) {
					//determines operation hours of current vehicle
					VehicleInfo vehicleInfo = vehicleOperationWindows.get(vehicleID);
					//determines if the capacity of the current vehicle is sufficient for the shipment's demand
					capacitySufficient = doesJobFitInVehicle(vehicleInfo.capacity(), pickupInfo.capacityDemand());
					//determines if the operation hours overlap with shipment's time window
					pickupOverlap = doTimeWindowsOverlap(vehicleInfo.operationWindow(), pickupInfo.pickupWindow());
					deliveryOverlap = doTimeWindowsOverlap(vehicleInfo.operationWindow(), deliveryInfo.deliveryWindow());

					//if the shipment fits in the current vehicle and the vehicle is in operation: shipment is transportable
					if (capacitySufficient && pickupOverlap && deliveryOverlap) {
						isTransportable = true;
					}
				}
				//if shipment is transportable => job is feasible
				if (!isTransportable) {
					logMessage("Job '{}' can not be handled by carrier '{}'.", shipmentID, carrier.getId().toString());
					nonFeasibleJob.put(shipmentID, "No sufficient vehicle available for this job.");
				}
			}

			/*
			 * SERVICES PART
			 */
			//see for-loop above. This loop does the same but for services instead of shipments
			for (CarrierService service : carrier.getServices().values()) {
				serviceWindows.put(service.getId(), new ServiceInfo(service.getServiceStaringTimeWindow(), service.getCapacityDemand()));
			}

			for (Id<? extends CarrierJob> serviceID : serviceWindows.keySet()) {
				ServiceInfo serviceInfo = serviceWindows.get(serviceID);
				boolean isTransportable = false;
				boolean capacityFits;
				boolean serviceOverlap;

				for (Id<Vehicle> vehicleID : vehicleOperationWindows.keySet()) {
					VehicleInfo vehicleInfo = vehicleOperationWindows.get(vehicleID);
					capacityFits = doesJobFitInVehicle(vehicleInfo.capacity(), serviceInfo.capacityDemand());
					serviceOverlap = doTimeWindowsOverlap(vehicleInfo.operationWindow(), serviceInfo.serviceWindow());

					if (capacityFits && serviceOverlap) {
						isTransportable = true;
					}
				}
				if (!isTransportable) {
					nonFeasibleJob.put(serviceID, "No sufficient vehicle for service");
				}
			}
			if (nonFeasibleJob.isEmpty()) {
				isCarrierCapable.put(carrier.getId(), true);
			} else {
				isCarrierCapable.put(carrier.getId(), false);
			}
		}
		//if every carrier has at least one vehicle in operation with sufficient capacity for all jobs, allCarriersCapable will be true
		isCarrierCapable.forEach((carrierId, value) -> {
			if (!value) {
				logMessage("Carrier " + carrierId + " can not handle all jobs.");
			}
		});
		if (isCarrierCapable.values().stream().allMatch(v -> v)) {
			return CheckResult.CHECK_SUCCESSFUL;
			} else {
			return CheckResult.CHECK_FAILED;
		}
	}

	/**
	 * This method will check whether all jobs have been correctly assigned to a tour, i.e. each job only occurs once
	 * (if the job is a shipment, pickup and delivery are two different jobs).
	 */
	public static CheckResult allJobsInTours(Carriers carriers, Level lvl) {
	setLogLevel(lvl);
		Map<Id<Carrier>, AllJobsInToursDetailedCheckResult> isCarrierCapable = new HashMap<>();
		boolean jobInToursMoreThanOnce = false;
		boolean jobIsMissing = false;
		for (Carrier carrier : carriers.getCarriers().values()) {
			List<Id<? extends CarrierJob>> serviceInTour = new LinkedList<>();
			List<String> shipmentInTour = new LinkedList<>();

			List<Id<? extends CarrierJob>> serviceList = new LinkedList<>();
			List<String> shipmentList = new LinkedList<>();

			Map<Id<? extends CarrierJob>, Integer> serviceCount = new HashMap<>();
			Map<String, Integer> shipmentCount = new HashMap<>();

			for (ScheduledTour tour : carrier.getSelectedPlan().getScheduledTours()) {
				for (Tour.TourElement tourElement : tour.getTour().getTourElements()) {
					//carrier only has one job-type: services or shipments
					//service is saved as an Id
					if (tourElement instanceof Tour.ServiceActivity serviceActivity) {
						serviceInTour.add(serviceActivity.getService().getId());
					}
					//shipment is saved as a string: jobId + activity type
					if (tourElement instanceof Tour.ShipmentBasedActivity shipmentBasedActivity) {
						shipmentInTour.add(shipmentBasedActivity.getShipment().getId() + " | " + shipmentBasedActivity.getActivityType());
					}
				}
			}

			//save all jobs the current carrier should do
			//shipments have to be picked up and delivered. To allow shipmentInTour being properly matched to shipmentList, shipments are saved with suffix CarrierConstants.PICKUP /.DELIVERY
			for (CarrierShipment shipment : carrier.getShipments().values()) {
				shipmentList.add(shipment.getId() + " | " + CarrierConstants.PICKUP);

				shipmentList.add(shipment.getId() + " | " + CarrierConstants.DELIVERY);
			}
			//services are saved with id only
			for (CarrierService service : carrier.getServices().values()) {
				serviceList.add(service.getId());
			}
			//count appearance of job ids
			for (Id<? extends CarrierJob> serviceId : serviceInTour) {
				serviceCount.put(serviceId, serviceCount.getOrDefault(serviceId, 0) + 1);
			}

			Iterator<Id<? extends CarrierJob>> serviceIterator = serviceList.iterator();
			while (serviceIterator.hasNext()) {
				Id<? extends CarrierJob> serviceId = serviceIterator.next();
				int count = serviceCount.getOrDefault(serviceId, 0);
				if (count == 1) {
					serviceIterator.remove();
				} else if (count > 1) {
					logMessage("Carrier '{}': Job '{}' is scheduled {} times!", carrier.getId(), serviceId, count);
					jobInToursMoreThanOnce = true;
				} else {
					logMessage("Carrier '{}': Job '{}' is not part of a tour!", carrier.getId(), serviceId);
					jobIsMissing = true;
				}
			}
			//count appearance of job ids
			for (String shipmentId : shipmentInTour) {
				shipmentCount.put(shipmentId, shipmentCount.getOrDefault(shipmentId, 0) + 1);
			}
			Iterator<String> shipmentIterator = shipmentList.iterator();
			while (shipmentIterator.hasNext()) {
				String shipmentId = shipmentIterator.next();
				int count = shipmentCount.getOrDefault(shipmentId, 0);
				if (count == 1) {
					shipmentIterator.remove();
				} else if (count > 1) {
					logMessage("Carrier '{}': Job '{}' is scheduled {} times!", carrier.getId(), shipmentId, count);
					jobInToursMoreThanOnce = true;
				} else {
					logMessage("Carrier '{}': Job '{}' is not part of a tour!", carrier.getId(), shipmentId);
					jobIsMissing = true;
				}
			}
			//if serviceList or shipmentList is NOT empty, at least one job is scheduled multiple times or not at all.
			if (!serviceList.isEmpty() || !shipmentList.isEmpty()) {
				if (jobInToursMoreThanOnce && !jobIsMissing) {
					isCarrierCapable.put(carrier.getId(), AllJobsInToursDetailedCheckResult.JOBS_SCHEDULED_MULTIPLE_TIMES);
				} else if (!jobInToursMoreThanOnce && jobIsMissing) {
					isCarrierCapable.put(carrier.getId(), AllJobsInToursDetailedCheckResult.NOT_ALL_JOBS_IN_TOURS);
				} else if (jobInToursMoreThanOnce && jobIsMissing) {
					isCarrierCapable.put(carrier.getId(), AllJobsInToursDetailedCheckResult.JOBS_MISSING_AND_OTHERS_MULTIPLE_TIMES_SCHEDULED);
				}
				//if serviceList or shipmentList is empty, all existing jobs (services or shipments) are scheduled only once.
			} else {
				logMessage("Carrier '{}': All jobs are scheduled once.", carrier.getId());
				isCarrierCapable.put(carrier.getId(), AllJobsInToursDetailedCheckResult.ALL_JOBS_IN_TOURS);
			}
		}
		//in the end, this check only returns CHECK_SUCCESSFUL or CHECK_FAILED, AllJobsInToursDetailedCheckResult can help identifying the inconsistency
		if (isCarrierCapable.values().stream().allMatch(v -> v.equals(AllJobsInToursDetailedCheckResult.ALL_JOBS_IN_TOURS))) {
			return CheckResult.CHECK_SUCCESSFUL;
		} else if (isCarrierCapable.values().stream().anyMatch(v -> v.equals(AllJobsInToursDetailedCheckResult.NOT_ALL_JOBS_IN_TOURS))) {
			return CheckResult.CHECK_FAILED;
		} else if (isCarrierCapable.values().stream().anyMatch(v -> v.equals(AllJobsInToursDetailedCheckResult.JOBS_SCHEDULED_MULTIPLE_TIMES))) {
			return CheckResult.CHECK_FAILED;
		} else if (isCarrierCapable.values().stream().anyMatch(v -> v.equals(AllJobsInToursDetailedCheckResult.JOBS_MISSING_AND_OTHERS_MULTIPLE_TIMES_SCHEDULED))) {
			return CheckResult.CHECK_FAILED;
		} else {
			logMessage("Unexpected outcome! Please check all input files.");
			return CheckResult.ERROR;
		}
	}
}
