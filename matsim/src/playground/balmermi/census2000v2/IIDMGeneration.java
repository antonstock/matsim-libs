/* *********************************************************************** *
 * project: org.matsim.*
 * PopulationCreation.java
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

package playground.balmermi.census2000v2;

import org.matsim.config.ConfigWriter;
import org.matsim.facilities.Facilities;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.gbl.Gbl;
import org.matsim.plans.MatsimPlansReader;
import org.matsim.plans.Plans;
import org.matsim.plans.PlansWriter;
import org.matsim.world.MatsimWorldReader;
import org.matsim.world.WorldWriter;
import org.matsim.world.algorithms.WorldCheck;
import org.matsim.world.algorithms.WorldValidation;

import playground.balmermi.census2000.data.Municipalities;
import playground.balmermi.census2000v2.data.Households;
import playground.balmermi.census2000v2.modules.PersonCreateFakePlanFromKnowledge;
import playground.balmermi.census2000v2.modules.WorldParseFacilityZoneMapping;
import playground.balmermi.census2000v2.modules.WorldWriteFacilityZoneMapping;

public class IIDMGeneration {

	//////////////////////////////////////////////////////////////////////
	// createPopulation()
	//////////////////////////////////////////////////////////////////////

	public static void createIIDM() {

		System.out.println("MATSim-DB: create iidm.");

		//////////////////////////////////////////////////////////////////////

		System.out.println("  extracting input directory... ");
		String indir = Gbl.getConfig().facilities().getInputFile();
		indir = indir.substring(0,indir.lastIndexOf("/"));
		System.out.println("    "+indir);
		System.out.println("  done.");

		System.out.println("  extracting output directory... ");
		String outdir = Gbl.getConfig().facilities().getOutputFile();
		outdir = outdir.substring(0,outdir.lastIndexOf("/"));
		System.out.println("    "+outdir);
		System.out.println("  done.");

		//////////////////////////////////////////////////////////////////////

		System.out.println("  reading world xml file...");
		final MatsimWorldReader worldReader = new MatsimWorldReader(Gbl.getWorld());
		worldReader.readFile(Gbl.getConfig().world().getInputFile());
		System.out.println("  done.");

		System.out.println("  reading facilities xml file...");
		Facilities facilities = (Facilities)Gbl.getWorld().createLayer(Facilities.LAYER_TYPE, null);
		new MatsimFacilitiesReader(facilities).readFile(Gbl.getConfig().facilities().getInputFile());
		Gbl.getWorld().complete();
		System.out.println("  done.");

		//////////////////////////////////////////////////////////////////////
		
		System.out.println("  parsing additional municipality information... ");
		Municipalities municipalities = new Municipalities(indir+"/gg25_2001_infos.txt");
		municipalities.parse();
		System.out.println("  done.");

		//////////////////////////////////////////////////////////////////////

		System.out.println("  running world modules... ");
		new WorldCheck().run(Gbl.getWorld());
		new WorldValidation().run(Gbl.getWorld());
		System.out.println("  done.");
		
		//////////////////////////////////////////////////////////////////////

		System.out.println("  parsing f2z_mapping... ");
		new WorldParseFacilityZoneMapping(indir+"/f2z_mapping.txt").run(Gbl.getWorld());
		System.out.println("  done.");

		//////////////////////////////////////////////////////////////////////

		System.out.println("  running world modules... ");
		new WorldCheck().run(Gbl.getWorld());
		new WorldValidation().run(Gbl.getWorld());
		System.out.println("  done.");
		
		//////////////////////////////////////////////////////////////////////

		System.out.println("  reding plans xml file... ");
		Plans plans = new Plans(Plans.NO_STREAMING);
		new MatsimPlansReader(plans).readFile(Gbl.getConfig().plans().getInputFile());
		System.out.println("  done.");

		//////////////////////////////////////////////////////////////////////
		
		// TODO: write some consistency tests

		//////////////////////////////////////////////////////////////////////

		System.out.println("  parsing households... ");
		Households households = new Households(municipalities);
		households.parse(indir+"/households.txt",plans);
		System.out.println("  done.");

		//////////////////////////////////////////////////////////////////////
		
		System.out.println("  running person modules... ");
		new PersonCreateFakePlanFromKnowledge().run(plans);
		System.out.println("  done.");
		
		//////////////////////////////////////////////////////////////////////

		System.out.println("  writing households txt file... ");
		households.writeTable(outdir+"/output_households.txt");
		System.out.println("  done.");

		System.out.println("  writing plans xml file... ");
		PlansWriter plans_writer = new PlansWriter(plans);
		plans_writer.write();
		System.out.println("  done.");

		System.out.println("  writing f2z_mapping... ");
		new WorldWriteFacilityZoneMapping(outdir+"/output_f2z_mapping.txt").run(Gbl.getWorld());
		System.out.println("  done.");

		System.out.println("  writing facilities xml file... ");
		FacilitiesWriter fac_writer = new FacilitiesWriter(facilities);
		fac_writer.write();
		System.out.println("  done.");

		System.out.println("  writing world xml file... ");
		WorldWriter world_writer = new WorldWriter(Gbl.getWorld());
		world_writer.write();
		System.out.println("  done.");

		System.out.println("  writing config xml file... ");
		ConfigWriter config_writer = new ConfigWriter(Gbl.getConfig());
		config_writer.write();
		System.out.println("  done.");

		System.out.println("done.");
		System.out.println();
	}

	//////////////////////////////////////////////////////////////////////
	// main
	//////////////////////////////////////////////////////////////////////

	public static void main(final String[] args) {

		Gbl.startMeasurement();

		Gbl.createConfig(args);
		Gbl.createWorld();

		createIIDM();

		Gbl.printElapsedTime();
	}
}
