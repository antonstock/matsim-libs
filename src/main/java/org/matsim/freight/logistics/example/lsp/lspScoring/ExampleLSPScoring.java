/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2022 by the members listed in the COPYING,        *
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

package org.matsim.freight.logistics.example.lsp.lspScoring;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.freight.carriers.*;
import org.matsim.freight.carriers.CarrierCapabilities.FleetSize;
import org.matsim.freight.carriers.events.CarrierServiceEndEvent;
import org.matsim.freight.carriers.events.eventhandler.CarrierServiceEndEventHandler;
import org.matsim.freight.logistics.*;
import org.matsim.freight.logistics.resourceImplementations.ResourceImplementationUtils;
import org.matsim.freight.logistics.shipment.LSPShipment;
import org.matsim.freight.logistics.shipment.ShipmentUtils;
import org.matsim.vehicles.VehicleType;

/* Example for customized scoring. Each customer that is visited will give a random tip between zero and five
 *
 *
 */

/*package-private*/ final class ExampleLSPScoring {

  private ExampleLSPScoring() {}

  private static LSP createLSPWithScorer(Network network) {

    // The Carrier for the resource of the sole LogisticsSolutionElement of the LSP is created
    var carrierVehicleType =
        CarrierVehicleType.Builder.newInstance(
                Id.create("CollectionCarrierVehicleType", VehicleType.class))
            .setCapacity(10)
            .setCostPerDistanceUnit(0.0004)
            .setCostPerTimeUnit(0.38)
            .setFixCost(49)
            .setMaxVelocity(50 / 3.6)
            .build();

    Id<Link> collectionLinkId = Id.createLinkId("(4 2) (4 3)");

    CarrierCapabilities capabilities =
        CarrierCapabilities.Builder.newInstance()
            //									      .addType(carrierVehicleType )
            .addVehicle(
                CarrierVehicle.newInstance(
                    Id.createVehicleId("CollectionVehicle"), collectionLinkId, carrierVehicleType))
            .setFleetSize(FleetSize.INFINITE)
            .build();

    Carrier carrier = CarriersUtils.createCarrier(Id.create("CollectionCarrier", Carrier.class));
    carrier.setCarrierCapabilities(capabilities);

    // The Resource i.e. the Resource is created
    // The scheduler for the Resource is created and added. This is where jsprit comes into play.
    LSPResource lspResource =
        ResourceImplementationUtils.CollectionCarrierResourceBuilder.newInstance(carrier, network)
            .setCollectionScheduler(
                ResourceImplementationUtils.createDefaultCollectionCarrierScheduler())
            .setLocationLinkId(collectionLinkId)
            .build();

    // The adapter is now inserted into the only LogisticsSolutionElement of the only
    // LogisticsSolution of the LSP
    LogisticChainElement logisticChainElement =
        LSPUtils.LogisticChainElementBuilder.newInstance(
                Id.create("CollectionElement", LogisticChainElement.class))
            .setResource(lspResource)
            .build();

    // The LogisticsSolutionElement is now inserted into the only LogisticsSolution of the LSP
    LogisticChain logisticChain =
        LSPUtils.LogisticChainBuilder.newInstance(
                Id.create("CollectionSolution", LogisticChain.class))
            .addLogisticChainElement(logisticChainElement)
            .build();

    // The initial plan of the lsp is generated and the assigner and the solution from above are
    // added
    LSPPlan lspPlan =
        LSPUtils.createLSPPlan()
            .setInitialShipmentAssigner(ResourceImplementationUtils.createSingleLogisticChainShipmentAssigner())
            .addLogisticChain(logisticChain);

    // The exogenous list of Resoruces for the SolutionScheduler is compiled and the Scheduler is
    // added to the LSPBuilder

    return LSPUtils.LSPBuilder.getInstance(Id.create("CollectionLSP", LSP.class))
        .setInitialPlan(lspPlan)
        .setLogisticChainScheduler(
            ResourceImplementationUtils.createDefaultSimpleForwardLogisticChainScheduler(
                Collections.singletonList(lspResource)))
        //				.setSolutionScorer(new TipScorer())
        .build();
  }

  private static Collection<LSPShipment> createInitialLSPShipments(Network network) {
    List<LSPShipment> shipmentList = new ArrayList<>();
    ArrayList<Link> linkList = new ArrayList<>(network.getLinks().values());

    // Create five LSPShipments that are located in the left half of the network.
    for (int i = 1; i < 6; i++) {
      Id<LSPShipment> id = Id.create(i, LSPShipment.class);
      ShipmentUtils.LSPShipmentBuilder builder = ShipmentUtils.LSPShipmentBuilder.newInstance(id);
      Random random = new Random(1);
      int capacityDemand = random.nextInt(4);
      builder.setCapacityDemand(capacityDemand);

      while (true) {
        Collections.shuffle(linkList, random);
        Link pendingFromLink = linkList.getFirst();
        if (pendingFromLink.getFromNode().getCoord().getX() <= 4000
            && pendingFromLink.getFromNode().getCoord().getY() <= 4000
            && pendingFromLink.getToNode().getCoord().getX() <= 4000
            && pendingFromLink.getToNode().getCoord().getY() <= 4000) {
          builder.setFromLinkId(pendingFromLink.getId());
          break;
        }
      }

      builder.setToLinkId(Id.createLinkId("(4 2) (4 3)"));
      TimeWindow endTimeWindow = TimeWindow.newInstance(0, (24 * 3600));
      builder.setEndTimeWindow(endTimeWindow);
      TimeWindow startTimeWindow = TimeWindow.newInstance(0, (24 * 3600));
      builder.setStartTimeWindow(startTimeWindow);
      builder.setDeliveryServiceTime(capacityDemand * 60);
      shipmentList.add(builder.build());
    }
    return shipmentList;
  }

  public static void main(String[] args) {

    Config config = prepareConfig();

    Scenario scenario = prepareScenario(config);

    Controler controler = prepareControler(scenario);

    // The VSP default settings are designed for person transport simulation. After talking to Kai,
    // they will be set to WARN here. Kai MT may'23
    controler
        .getConfig()
        .vspExperimental()
        .setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
    controler.run();

    for (LSP lsp2 : LSPUtils.getLSPs(scenario).getLSPs().values()) {
      System.out.println("The tip of all customers was: " + lsp2.getSelectedPlan().getScore());
    }
  }

  static Controler prepareControler(Scenario scenario) {
    // Start the Mobsim one iteration is sufficient for scoring
    Controler controler = new Controler(scenario);
    controler.addOverridingModule(new LSPModule());
    controler.addOverridingModule(
        new AbstractModule() {
          @Override
          public void install() {
            bind(LSPScorerFactory.class).toInstance(TipScorer::new);
          }
        });
    return controler;
  }

  static Scenario prepareScenario(Config config) {
    Scenario scenario = ScenarioUtils.loadScenario(config);

    // Create LSP and shipments
    LSP lsp = createLSPWithScorer(scenario.getNetwork());
    Collection<LSPShipment> shipments = createInitialLSPShipments(scenario.getNetwork());

    // assign the shipments to the LSP
    for (LSPShipment shipment : shipments) {
      lsp.assignShipmentToLSP(shipment);
    }

    // schedule the LSP with the shipments and according to the scheduler of the Resource
    lsp.scheduleLogisticChains();

    // Prepare LSPModule and add the LSP
    LSPs lsps = new LSPs(Collections.singletonList(lsp));
    LSPUtils.addLSPs(scenario, lsps);
    return scenario;
  }

  static Config prepareConfig() {
    // Set up required MATSim classes
    Config config = ConfigUtils.createConfig();

    config.network().setInputFile("scenarios/2regions/2regions-network.xml");

    config.controller().setLastIteration(0);
    config.controller().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

    var freightConfig = ConfigUtils.addOrGetModule(config, FreightCarriersConfigGroup.class);
    freightConfig.setTimeWindowHandling(FreightCarriersConfigGroup.TimeWindowHandling.ignore);
    return config;
  }

  /*package-private*/ static class TipScorer
      implements LSPScorer, LSPSimulationTracker<LSP>, CarrierServiceEndEventHandler {

    private static final Logger log = LogManager.getLogger(TipScorer.class);

    private final Random tipRandom;
    private double tipSum;

    /*package-private*/ TipScorer() {
      tipRandom = new Random(1);
      tipSum = 0;
    }

    @Override
    public double getScoreForCurrentPlan() {
      return tipSum;
    }

    @Override
    public void setEmbeddingContainer(LSP pointer) {
      // backpointer not needed here, therefor not memorizing it.  kai, jun'22
    }

    @Override
    public void handleEvent(CarrierServiceEndEvent event) {
      double tip = tipRandom.nextDouble() * 5;
      log.warn("tipSum={}; tip={}", tipSum, tip);
      tipSum += tip;
    }

    @Override
    public void reset(int iteration) {
      tipSum = 0.;
    }
  }
}
