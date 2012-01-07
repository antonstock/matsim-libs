/*******************************************************************************
 * Copyright (C) 2011 Stefan Schroeder.
 * eMail: stefan.schroeder@kit.edu
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.matsim.contrib.freight.vrp.algorithms.rr.tourAgents;

import org.matsim.contrib.freight.vrp.basics.Tour;
import org.matsim.contrib.freight.vrp.basics.Vehicle;



/**
 * 
 * @author stefan schroeder
 *
 */

public class RRTourAgentFactory implements TourAgentFactory{

	private TourStatusProcessor tourStatusProcessor;
	
	private TourFactory tourBuilder;
	
	public RRTourAgentFactory(TourStatusProcessor tourStatusProcessor, TourFactory tourBuilder) {
		super();
		this.tourStatusProcessor = tourStatusProcessor;
		this.tourBuilder = tourBuilder;
	}

	@Override
	public TourAgent createTourAgent(Tour tour, Vehicle vehicle) {
		RRTourAgent tourAgent = new RRTourAgent(vehicle, tour, tourStatusProcessor, tourBuilder);
		return tourAgent;
	}
}
