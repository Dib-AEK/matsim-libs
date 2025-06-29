package org.matsim.contrib.discrete_mode_choice.models;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceModel;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceModel.FallbackBehaviour;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceModel.NoFeasibleChoiceException;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.constraints.CompositeTripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.filters.CompositeTripFilter;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.DefaultModeAvailability;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.ModeAvailability;
import org.matsim.contribs.discrete_mode_choice.model.tour_based.TripFilter;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripBasedModel;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripBasedModelLLM;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.utilities.MaximumSelector;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilitySelectorFactory;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup.ActivityDurationInterpretation;
import org.matsim.core.config.groups.PlansConfigGroup.TripDurationHandling;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

public class TripBasedModelLLMTest {
	public static void main(String[] args) throws DiscreteModeChoiceModel.NoFeasibleChoiceException, IOException, URISyntaxException {
		TripFilter tripFilter = new CompositeTripFilter(Collections.emptySet());
		ModeAvailability modeAvailability = new DefaultModeAvailability(Arrays.asList("car", "pt", "walk","bike"));
		TripConstraintFactory constraintFactory = new CompositeTripConstraintFactory();
		DiscreteModeChoiceModel.FallbackBehaviour fallbackBehaviour = DiscreteModeChoiceModel.FallbackBehaviour.EXCEPTION;
		ConstantTripEstimator estimator = new ConstantTripEstimator();
		UtilitySelectorFactory selectorFactory = new MaximumSelector.Factory();

		Activity originActivity = PopulationUtils.createActivityFromCoord("home", new Coord(0.0, 0.0));
		originActivity.setEndTime(0.0);

		Activity destinationActivity = PopulationUtils.createActivityFromCoord("work", new Coord(600, 300));
		originActivity.setEndTime(0.0);

		List<DiscreteModeChoiceTrip> trips = Collections
			.singletonList(new DiscreteModeChoiceTrip(originActivity, destinationActivity, "walk", null, 0, 0, 0, new AttributesImpl()));

		TripBasedModelLLM model = new TripBasedModelLLM(estimator, tripFilter, modeAvailability, constraintFactory,
			selectorFactory, fallbackBehaviour,
			TimeInterpretation.create(PlansConfigGroup.ActivityDurationInterpretation.tryEndTimeThenDuration,
				PlansConfigGroup.TripDurationHandling.shiftActivityEndTimes));

		List<TripCandidate> result;

		// Test 1
		estimator.setAlternative("car", -1.0);
		estimator.setAlternative("pt", -1.5);
		estimator.setAlternative("walk", -2.0);
		estimator.setAlternative("bike", -2.0);

		for (int i = 0; i < 10; i++) {
			result = model.chooseModes(null, trips, new Random(0));
		}
	}
}
