package org.matsim.contribs.discrete_mode_choice.model.tour_based;

import java.util.List;
import java.util.stream.Collectors;

import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

/**
 * Default implementation for a TourCandidate.
 *
 * @author sebhoerl
 */
public class DefaultTourCandidate implements TourCandidate {
	final private double utility;
	final private List<TripCandidate> tripCandidates;

	public DefaultTourCandidate(double utility, List<TripCandidate> tripCandidates) {
		this.utility = utility;
		this.tripCandidates = tripCandidates;
	}

	@Override
	public double getUtility() {
		return utility;
	}

	@Override
	public List<TripCandidate> getTripCandidates() {
		return tripCandidates;
	}

	public String getModes() {
		List<String> modes = tripCandidates.stream()
										   .map(TripCandidate::getMode)
										   .collect(Collectors.toList());
		return String.join(",", modes);
	}

	public String getUtilities() {
		return tripCandidates.stream()
			.map(t -> String.format("%.3f", t.getUtility())) // Formats to 3 decimal places
			.collect(Collectors.joining(","));
	}

}
