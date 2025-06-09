package org.matsim.contribs.discrete_mode_choice.model.trip_based;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceModel;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.ModeAvailability;
import org.matsim.contribs.discrete_mode_choice.model.tour_based.TripFilter;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilityCandidate;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilitySelector;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilitySelectorFactory;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.core.utils.timing.TimeTracker;
import org.matsim.contribs.discrete_mode_choice.llm.LLM;

/**
 * This class defines a trip-based discrete choice model.
 *
 * @author Abdelkader Dib
 *
 */
public class TripBasedModelLLM implements DiscreteModeChoiceModel {
	private final static Logger logger = LogManager.getLogger(TripBasedModelLLM.class);

	private final TripEstimator estimator;
	private final TripFilter tripFilter;
	private final ModeAvailability modeAvailability;
	private final TripConstraintFactory constraintFactory;
	private final UtilitySelectorFactory selectorFactory;
	private final FallbackBehaviour fallbackBehaviour;
	private final TimeInterpretation timeInterpretation;

	public TripBasedModelLLM(TripEstimator estimator, TripFilter tripFilter, ModeAvailability modeAvailability,
							 TripConstraintFactory constraintFactory, UtilitySelectorFactory selectorFactory,
							 FallbackBehaviour fallbackBehaviour, TimeInterpretation timeInterpretation) {
		this.estimator = estimator;
		this.tripFilter = tripFilter;
		this.modeAvailability = modeAvailability;
		this.constraintFactory = constraintFactory;
		this.selectorFactory = selectorFactory;
		this.fallbackBehaviour = fallbackBehaviour;
		this.timeInterpretation = timeInterpretation;
	}

	@Override
	public List<TripCandidate> chooseModes(Person person, List<DiscreteModeChoiceTrip> trips, Random random)
		throws NoFeasibleChoiceException, IOException, URISyntaxException {
		List<String> modes = new ArrayList<>(modeAvailability.getAvailableModes(person, trips));
		TripConstraint constraint = constraintFactory.createConstraint(person, trips, modes);

		List<TripCandidate> tripCandidates = new ArrayList<>(trips.size());
		List<String> tripCandidateModes = new ArrayList<>(trips.size());

		LLM llmSelector = new LLM("llama3.2:3b", 1.2f);

		int tripIndex = 0;
		TimeTracker timeTracker = new TimeTracker(timeInterpretation);

		for (DiscreteModeChoiceTrip trip : trips) {
			timeTracker.addActivity(trip.getOriginActivity());
			trip.setDepartureTime(timeTracker.getTime().seconds());

			TripCandidate finalTripCandidate = null;

			if (tripFilter.filter(person, trip)) {
				tripIndex++;

				// Build agent and trip attribute maps
				Map<String, Object> agentAttrs = getAgentAttributes(person);
				Map<String, Object> tripAttrs = getTripAttributes(trip);

				List<String> validModes = modes;
				for (String mode : modes) {
					if (!constraint.validateBeforeEstimation(trip, mode, tripCandidateModes)) {
						validModes.remove(mode);
					}
					// Ask LLM to choose the mode
					String chosenMode;
					try {
						chosenMode = llmSelector.ask(agentAttrs, tripAttrs, validModes);
					} catch (Exception e) {
						throw new RuntimeException("Error querying LLM for mode choice", e);
					}

					// Estimate trip with chosen mode
					finalTripCandidate = estimator.estimateTrip(person, chosenMode, trip, tripCandidates);

				}
			} else {
				finalTripCandidate = createFallbackCandidate(person, trip, tripCandidates);
			}

			tripCandidates.add(finalTripCandidate);
			tripCandidateModes.add(finalTripCandidate.getMode());
			timeTracker.addDuration(finalTripCandidate.getDuration());
		}

		return tripCandidates;
	}


	private TripCandidate createFallbackCandidate(Person person, DiscreteModeChoiceTrip trip,
												  List<TripCandidate> tripCandidates) {
		return estimator.estimateTrip(person, trip.getInitialMode(), trip, tripCandidates);
	}

	private List<TripCandidate> handleIgnoreAgent(int tripIndex, Person person, List<DiscreteModeChoiceTrip> trips) {
		List<TripCandidate> candidates = new ArrayList<>(trips.size());

		for (DiscreteModeChoiceTrip trip : trips) {
			candidates.add(estimator.estimateTrip(person, trip.getInitialMode(), trip, candidates));
		}

		logger.warn(buildFallbackMessage(tripIndex, person, "Setting whole plan back to initial modes."));
		return candidates;
	}

	private String buildFallbackMessage(int tripIndex, Person person, String appendix) {
		return String.format("No feasible mode choice candidate for trip %d of agent %s. %s", tripIndex,
			person.getId().toString(), appendix);
	}

	private String buildIllegalUtilityMessage(int tripIndex, Person person, TripCandidate candidate) {
		return String.format("Received illegal utility for trip %d (%s) of agent %s. Continuing with next candidate.",
			tripIndex, candidate.getMode(), person.getId().toString());
	}

	private Map<String, Object> getTripAttributes(DiscreteModeChoiceTrip trip) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("originActivity", trip.getOriginActivity().getType());
		attrs.put("destinationActivity", trip.getDestinationActivity().getType());
		attrs.put("departureTime", trip.getDepartureTime());

		double euclideanDistance = CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(),
			trip.getDestinationActivity().getCoord()) * 1e-3;
		attrs.put("euclideanDistance", euclideanDistance);
		return attrs;
	}

	private Map<String, Object> getAgentAttributes(Person person) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("age", person.getAttributes().getAttribute("age"));
		attrs.put("gender", person.getAttributes().getAttribute("sex"));
		attrs.put("income", person.getAttributes().getAttribute("income"));
		attrs.put("license", person.getAttributes().getAttribute("license"));
		attrs.put("carAvailable", person.getAttributes().getAttribute("carAvailable"));
		return attrs;
	}
}
