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
	private final LLM llmSelector;
	public TripBasedModelLLM(TripEstimator estimator, TripFilter tripFilter, ModeAvailability modeAvailability,
							 TripConstraintFactory constraintFactory, UtilitySelectorFactory selectorFactory,
							 FallbackBehaviour fallbackBehaviour, TimeInterpretation timeInterpretation) throws IOException, URISyntaxException {
		this.estimator = estimator;
		this.tripFilter = tripFilter;
		this.modeAvailability = modeAvailability;
		this.constraintFactory = constraintFactory;
		this.selectorFactory = selectorFactory;
		this.fallbackBehaviour = fallbackBehaviour;
		this.timeInterpretation = timeInterpretation;

		String modelName = "gemma3:4b";
		float temperature = 1.0F;
		this.llmSelector = new LLM(modelName, temperature);
		System.out.println("LLM model is used for mode selection.");
		System.out.println("Model: " + modelName);
		System.out.println("Temperature: " + temperature);
	}

	@Override
	public List<TripCandidate> chooseModes(Person person, List<DiscreteModeChoiceTrip> trips, Random random)
		throws NoFeasibleChoiceException, IOException, URISyntaxException {

		List<String> modes = new ArrayList<>(modeAvailability.getAvailableModes(person, trips));
		TripConstraint constraint = constraintFactory.createConstraint(person, trips, modes);

		List<TripCandidate> tripCandidates = new ArrayList<>(trips.size());
		List<String> tripCandidateModes = new ArrayList<>(trips.size());

		TimeTracker timeTracker = new TimeTracker(timeInterpretation);

		int tripIndex = 0;

		for (DiscreteModeChoiceTrip trip : trips) {
			timeTracker.addActivity(trip.getOriginActivity());
			trip.setDepartureTime(timeTracker.getTime().seconds());

			TripCandidate selectedCandidate;

			if (tripFilter.filter(person, trip)) {
				tripIndex++;

				try {
					selectedCandidate = selectValidTripCandidate(
						person, trip, modes, tripCandidateModes, tripCandidates,
						constraint, tripIndex
					);
				} catch (NoFeasibleChoiceException e) {
					switch (fallbackBehaviour) {
						case INITIAL_CHOICE:
							logger.info(buildFallbackMessage(tripIndex, person, "Setting trip back to initial mode."));
							selectedCandidate = createFallbackCandidate(person, trip, tripCandidates);
							break;
						case IGNORE_AGENT:
							return handleIgnoreAgent(tripIndex, person, trips);
						case EXCEPTION:
						default:
							throw new NoFeasibleChoiceException(buildFallbackMessage(tripIndex, person, ""));
					}
				}
			} else {
			 	selectedCandidate = createFallbackCandidate(person, trip, tripCandidates);
			}

			tripCandidates.add(selectedCandidate);
			tripCandidateModes.add(selectedCandidate.getMode());
			timeTracker.addDuration(selectedCandidate.getDuration());
		}

		return tripCandidates;
	}

	private TripCandidate selectValidTripCandidate(
		Person person,
		DiscreteModeChoiceTrip trip,
		List<String> allModes,
		List<String> tripCandidateModes,
		List<TripCandidate> tripCandidates,
		TripConstraint constraint,
		int tripIndex
	) throws NoFeasibleChoiceException {

		List<String> validModes = new ArrayList<>(allModes);

		while (!validModes.isEmpty()) {
			// Filter modes before estimation
			validModes.removeIf(mode -> !constraint.validateBeforeEstimation(trip, mode, tripCandidateModes));

			if (validModes.isEmpty()) break;

			String chosenMode;
			try {
				chosenMode = llmSelector.askMode(person, trip, validModes, tripCandidates, this.estimator);
			} catch (Exception e) {
				// throw new RuntimeException("Error querying LLM for mode choice", e);
				throw new NoFeasibleChoiceException(buildFallbackMessage(tripIndex, person, ""));
			}

			TripCandidate candidate = estimator.estimateTrip(person, chosenMode, trip, tripCandidates);

			if (!Double.isFinite(candidate.getUtility())) {
				logger.warn(buildIllegalUtilityMessage(tripIndex, person, candidate));
				validModes.remove(chosenMode); // Remove and retry
				continue;
			}

			if (!constraint.validateAfterEstimation(trip, candidate, tripCandidates)) {
				validModes.remove(chosenMode); // Remove and retry
				continue;
			}

			return candidate; // Valid candidate
		}

		throw new NoFeasibleChoiceException(buildFallbackMessage(tripIndex, person, ""));
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
}
