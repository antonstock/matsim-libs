/* *********************************************************************** *
 * project: org.matsim.*
 * ORCAAgentFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.gregor.sim2d_v4.simulation;

import org.matsim.core.mobsim.qsim.qnetsimengine.QVehicle;

import playground.gregor.sim2d_v4.simulation.physics.DelegableSim2DAgent;
import playground.gregor.sim2d_v4.simulation.physics.FailsafeAgentImpl;
import playground.gregor.sim2d_v4.simulation.physics.ORCAAgent;
import playground.gregor.sim2d_v4.simulation.physics.Sim2DAgent;

public class ORCAAgentFactory implements Sim2DAgentFactory {

	@Override
	public Sim2DAgent buildAgent(QVehicle veh, float spawnX, float spawnY) {
		DelegableSim2DAgent delegate = new ORCAAgent(veh, spawnX, spawnY);
		Sim2DAgent agent = new FailsafeAgentImpl(delegate);
		return delegate;
	}

}
