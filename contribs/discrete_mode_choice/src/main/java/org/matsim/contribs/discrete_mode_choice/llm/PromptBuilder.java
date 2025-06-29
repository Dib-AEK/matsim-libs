package org.matsim.contribs.discrete_mode_choice.llm;

import org.apache.commons.compress.harmony.pack200.NewAttribute;
import org.geotools.xml.xsi.XSISimpleTypes;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripEstimator;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.core.utils.geometry.CoordUtils;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class build the prompts used by the LLM
 */

public class PromptBuilder {

	public static String buildTransportModeSystemPrompt() {
		return """
				You are an AI agent simulating Swiss transportation mode choices for a microsimulation platform.
				Your task is to predict the most likely transport mode chosen by a given commuter (called also agent in the simulation) for a specified trip.

				**Decision Framework:**

				1. *Essential Constraints* (Must be satisfied):
							- License + access to a car for 'car'
							- Physical capability for 'bike' or 'walk'
							- Time window feasibility (arrival deadline)

				2. *Primary Considerations* (score 1-10):
							- Total travel time (door-to-door)
							- Out-of-pocket costs (fuel/tolls vs PT fare vs parking fees)
							- Physical comfort (fitness level)
							- Cognitive ease (route complexity, parking stress)

				3. *Secondary Influences*:
							- Environmental consciousness
							- Trip urgency
							- Passenger accompaniment needs
							- Subscription discounts
							- Historical habits (simulate consistency)

				**Special Cases:**
				- <0.5km trips: 80%% walk unless carrying heavy items
				- Long distances (>8km): Only public transportation or car
				- Work commutes: Prioritize speed/reliability
				- Leisure trips: Prioritize cost/enjoyment
				- PT transfers >3: Apply time penalty multiplier
				- Bike uphill >10% grade: Apply duration multiplier

				**Available Transport Modes:**
				- car
				- pt (public transport)
				- bike
				- walk
				- car_passenger

				**Mode shares:**
					In switzerland, the mode shares are as follows:
						- car: 45%
						- pt: 20%
						- walk, bike, and car passenger: 35%
				The average salary in switzerland is around 6000 CHF.

				**Example Inputs and Outputs:**

				Persona: "Urban university student with environmental values"
				Scenario: "Morning commute to university, 4 km, no heavy items, good weather, pt pass available"
				Output: pt

				Persona: "Rural office manager with two children"
				Scenario: "Going to work, 7 km, at 8am"
				Output: car

				Persona: "65 years old mountain village retiree who walks everywhere"
				Scenario: "Grocery shopping, 0.6 km, at 10 am."
				Output: walk

				**Important Instructions:**
				- Always reply only with the selected transport mode
				- Don't be deterministic, the selection should be stochastic to reflect real world variability
				- Do not include any explanation, reasoning, or markdown
				- Use only one of the five allowed modes: car, pt, bike, walk, car_passenger
				\s""";
	}

	public static String buildPersonaSystemPrompt() {
		return """
				You are an AI agent simulating Swiss transportation mode choices for a microsimulation platform.
				Your task is to generate a realistic commuter persona based on demographic and geographic factors.

				**Persona Generation Guidelines:**
				1. Create a concise, 3-trait description of the commuter that reflects:
							- Occupation (e.g., teacher, tech worker, student)
							- Location type and mode preferences (urban, suburban, rural)
							- Lifestyle or behavioral traits (environmentally conscious, time-sensitive, budget-focused, like driving)
				2. Consider Swiss-specific characteristics:
							- Alpine terrain influences bike usage feasibility
							- Urban areas have excellent public transport and car-sharing options
							- Suburban areas may rely more on cars or trains
							- Rural areas often depend on cars due to sparse PT coverage
							- High standard of living affects travel preferences

				3. Include relevant life circumstances:
							- Family status (single, parent, caregiver)
							- Work schedule (fixed hours, shift work)
							- Access to vehicles (car ownership, bike availability)
							- Subscription services (SBB Half-Fare, car-sharing memberships)

				**Mode shares:**
					In switzerland, the mode shares are as follows:
						- car: 45%
						- pt: 20%
						- walk, bike, and car passenger: 35%
				The average salary in switzerland is around 6000 CHF.

				**Examples of Personas:**
				- "Urban university student with environmental values and biking preferences for small distances"
				- "Suburban nurse working night shifts without a car"
				- "Rural office manager with two children and a family car. He has free parking at work"
				- "Mountain village retiree who walks everywhere, but prefer public transport for long trips"
				- "Zurich-based IT consultant with an SBB Half-Fare card, with no parking place at work, but a beautiful SUV for leisure"
				- "Geneva-based high salary bank consultant with car preferences, he doesn't like being in crowded buses"

				**Important Instructions:**
				- Always reply only with the generated persona
				- Do not include any explanation, reasoning, or markdown
				- Format: plain text, one-line persona description
				\s""";
	}

	public static String buildMessagePrompt(Person person, String persona, DiscreteModeChoiceTrip trip,
											List<String> validModes, List<TripCandidate> tripCandidates,
											TripEstimator estimator) {
		Map<String, Object> agentAttributes = getAgentAttributes(person);
		Map<String, Object> tripAttributes = getTripAttributes(trip, person, validModes, tripCandidates, estimator);

		// Extract attributes with type safety
		int age = Integer.parseInt(agentAttributes.get("age").toString());
		String gender = "m".equals(agentAttributes.get("gender")) ? "male" : "female";
		String municipalityType = agentAttributes.get("municipalityType").toString();
		String income = agentAttributes.get("income").toString();
		String canton = agentAttributes.get("canton").toString();

		boolean hasLicense = Boolean.parseBoolean(agentAttributes.get("license").toString());
		boolean hasCar = Boolean.parseBoolean(agentAttributes.get("carAvailable").toString());
		boolean ptSub = Boolean.parseBoolean(agentAttributes.get("ptSubscription").toString());
		boolean employed = Boolean.parseBoolean(agentAttributes.get("employed").toString());

		// Trip details
		String origin = tripAttributes.get("originActivity").toString();
		String dest = tripAttributes.get("destinationActivity").toString();
		double distance = (Double) tripAttributes.get("euclideanDistance");

		double departureTimeInSeconds = (double) tripAttributes.get("departureTime");
		String departureTime = LocalTime.MIN.plusSeconds((long) departureTimeInSeconds).toString();

		// Mode durations with processing
		StringBuilder durations = new StringBuilder();
		validModes.forEach(mode -> {
			double duration = ((Double) tripAttributes.get("duration_" + mode)) / 60.0;
			durations.append(String.format("- %s: %.1f min", mode, duration));

			// Add terrain modifiers for Swiss context
			if ("bike".equals(mode) && distance > 5) {
				durations.append(" [consider elevation and physical capability]");
			}
			if ("walk".equals(mode) && distance > 2) {
				durations.append(" [consider physical capability]");
			}
			durations.append("\n");
		});

		// passed trips
		String passedTrips = describePastTrips(tripCandidates);

		return String.format("""
            **Commute Scenario**
                Origin: %s → Destination: %s
                Departure: %s
                Distance: %.2f km
                Available Modes: %s

            **Agent Profile**
                Age: %d (%s)
                Gender: %s
                Residence canton: %s
                Type of residence municipality: %s
                Income: %s
                License: %s | Car Access: %s
                PT Sub: %s | Employed: %s

            **A probable persona of this agent**
                %s

            **Passed trips of the day:**
                %s

            **Travel Options with estimated travel durations**
                %s

            Instructions:
            Pick **only one** of the available transport modes options that best fits this individual and trip.
            Respond with just one word, that should be a transport mode in any case.
           \s""",
			origin, dest, departureTime,
			distance, String.join(", ", validModes),
			age, getAgeGroup(age), gender, canton, municipalityType, income,
			yesNo(hasLicense), yesNo(hasCar),
			ptSub, yesNo(employed),
			persona,
			passedTrips,
			durations.toString());
	}

	private static String getAgeGroup(int age) {
		if (age < 18) return "minor";
		if (age < 30) return "young adult";
		if (age < 50) return "middle-aged";
		if (age < 65) return "senior professional";
		return "retiree";
	}

	private static String yesNo(boolean value) {
		return value ? "Yes" : "No";
	}

	private static String describePastTrips(List<TripCandidate> passedTrips) {
		if (passedTrips == null || passedTrips.isEmpty()) {
			return "This is the first trip of the day.";
		}

		StringBuilder sb = new StringBuilder("Past trips of the day:\n");

		for (TripCandidate trip : passedTrips) {
			double duration = ((Double) trip.getDuration()) / 60.0;
			String mode = trip.getMode();
			String tripDesc = String.format("- %s: %.1f min\n", mode, duration);
			sb.append(tripDesc);
		}

		return sb.toString();
	}

	public static String buildPersonaPromptFromAttributes(Person person) {
		Map<String, Object> agentAttributes = getAgentAttributes(person);
		int age = Integer.parseInt(agentAttributes.get("age").toString());
		String gender = "m".equals(agentAttributes.get("gender")) ? "male" : "female";
		boolean hasLicense = Boolean.parseBoolean(agentAttributes.get("license").toString());
		boolean hasCar = Boolean.parseBoolean(agentAttributes.get("carAvailable").toString());
		boolean ptSub = Boolean.parseBoolean(agentAttributes.get("ptSubscription").toString());
		boolean employed = Boolean.parseBoolean(agentAttributes.get("employed").toString());

		String municipalityType = agentAttributes.get("municipalityType").toString();
		String income = agentAttributes.get("income").toString();
		String canton = agentAttributes.get("canton").toString();

		return String.format("""
			**Task: Generate a commuter persona**

			Based on the following agent attributes, create a realistic Swiss commuter profile.

			**Agent Attributes**
			- Age: %d
			- Gender: %s
			- Employed: %s
			- Residence canton: %s
			- Type of residence municipality: %s
			- Income: %s
			- Has driver's license: %s
			- Car available: %s
			- Public transport subscription: %s

			Each trait should capture an aspect of:
			1. Location / urban context
			2. Occupation / lifestyle / value of time
			3. Behavior / values affecting mobility choice

			Output only the persona as one line of text.
			""",
			age,
			gender,
			yesNo(employed),
			canton, municipalityType, income,
			yesNo(hasLicense),
			yesNo(hasCar),
			ptSub
		);
	}

	private static Map<String, Object> getTripAttributes(DiscreteModeChoiceTrip trip, Person person, List<String> validModes,
														 List<TripCandidate> tripCandidates, TripEstimator estimator) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("originActivity", trip.getOriginActivity().getType());
		attrs.put("destinationActivity", trip.getDestinationActivity().getType());
		attrs.put("departureTime", trip.getDepartureTime());

		double euclideanDistance = CoordUtils.calcEuclideanDistance(trip.getOriginActivity().getCoord(),
			trip.getDestinationActivity().getCoord()) * 1e-3;
		attrs.put("euclideanDistance", euclideanDistance);

		for (String mode : validModes) {
			TripCandidate candidate = estimator.estimateTrip(person, mode, trip, tripCandidates);
			double durationSeconds = candidate.getDuration();
			attrs.put("duration_" + mode, durationSeconds);
		}

		return attrs;
	}

	private static Map<String, Object> getAgentAttributes(Person person) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("age", person.getAttributes().getAttribute("age"));
		attrs.put("gender", person.getAttributes().getAttribute("sex"));
		attrs.put("license", person.getAttributes().getAttribute("hasLicense"));
		attrs.put("carAvailable", person.getAttributes().getAttribute("carAvail"));
		attrs.put("employed", person.getAttributes().getAttribute("employed"));

		// public transport subscription
		boolean ptSubscription = (boolean) person.getAttributes().getAttribute("ptHasGA") ||
			(boolean) person.getAttributes().getAttribute("ptHasVerbund");
		boolean hasHalbtax = (boolean) person.getAttributes().getAttribute("ptHasHalbtax");

		if (ptSubscription) {
			attrs.put("ptSubscription", "Yes");
		} else if (hasHalbtax) {
			attrs.put("ptSubscription", "Has SBB Half fare card (50% reduction)");
		} else {
			attrs.put("ptSubscription", "No");
		}
		// Municipality type
		String municipalityType = (String) person.getAttributes().getAttribute("municipalityType");
		attrs.put("municipalityType", Objects.requireNonNullElse(municipalityType, "Not known"));

		// canton name
		String cantonName = (String) person.getAttributes().getAttribute("cantonName");
		attrs.put("canton", Objects.requireNonNullElse(cantonName, "Not known"));

		// income
		Object incomeClass = person.getAttributes().getAttribute("incomeClass");
		attrs.put("income", getIncomeClass(incomeClass));

		return attrs;
	}

	private static String getIncomeClass(Object incomeClass) {
		if (null == incomeClass){return "unknown";}

		int incomeClassInt = Integer.parseInt(incomeClass.toString());
		if (incomeClassInt<0){return "unknown";}

		return switch (incomeClassInt) {
			case 0 -> "Less than CHF 2000";
			case 1 -> "2000 to 4000 CHF";
			case 2 -> "4001 to 6000 CHF";
			case 3 -> "6001 to 8000 CHF";
			case 4 -> "8001 to 10000 CHF";
			case 5 -> "10001 to 12000 CHF";
			case 6 -> "12001 to 14000 CHF";
			case 7 -> "14001 to 16000 CHF";
			case 8 -> "More than 16000 CHF";
			default -> "unknown";
		};
	}
}
