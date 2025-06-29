package org.matsim.contribs.discrete_mode_choice.llm;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.utils.Options;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceModel;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripEstimator;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

public class LLM {

	private static final List<String> VALID_MODES = List.of("pt", "car", "bike", "walk","car_passenger");

	private final OllamaAPI ollamaAPI;
	private final String model;
	private final float temperature;
	private int counter;

	public LLM(String model, float temperature) throws IOException, URISyntaxException {
		this(model, temperature, Utilities.getFromConfig("host"));
	}

	public LLM(String model, float temperature, String host) throws IOException, URISyntaxException {
		this.ollamaAPI = new OllamaAPI(host);
		this.ollamaAPI.setVerbose(false);
		this.ollamaAPI.setRequestTimeoutSeconds(60L);
		this.model = model;
		this.temperature = temperature;
		this.counter = 0;
	}

	public String ask(String systemPrompt, String message) throws ToolInvocationException, OllamaBaseException, IOException, InterruptedException {
		OllamaChatRequest requestMode = buildChatRequest(systemPrompt, message);
		return sendRequest(requestMode);
	}

	/**
	 * Core simulation query method. Returns one of: pt, car, bike, walk
	 */

	public String askMode(Person person, DiscreteModeChoiceTrip trip, List<String> validModes, List<TripCandidate> tripCandidates, TripEstimator estimator)
		throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException, DiscreteModeChoiceModel.NoFeasibleChoiceException {
		boolean onlyOneMode = validModes.size() == 1;
		if (onlyOneMode){
			return validModes.getFirst();
		}

		String persona = getPersona(person);
		String systemPrompt = PromptBuilder.buildTransportModeSystemPrompt();
		String tripMessage  = PromptBuilder.buildMessagePrompt(person, persona, trip, validModes, tripCandidates, estimator);
		String response = ask(systemPrompt, tripMessage);

		if (counter<2) {
			System.out.println("LLM response: " + response);
		}
		counter = counter+1;
		return normalizeResponse(response, tripMessage);
	}

	private OllamaChatRequest buildChatRequest(String systemPrompt, String userPrompt) {
		Map<String, Object> optionsMap = new HashMap<>();
		optionsMap.put("temperature", this.temperature);
		optionsMap.put("think", false);
		Options options = new Options(optionsMap);

		return OllamaChatRequestBuilder.getInstance(this.model)
			.withMessage(OllamaChatMessageRole.SYSTEM, systemPrompt)
			.withMessage(OllamaChatMessageRole.USER, userPrompt)
			.withOptions(options)
			.build();
	}

	private String sendRequest(OllamaChatRequest request)
		throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException {
		OllamaChatResult result = ollamaAPI.chat(request);
		return result.getResponseModel().getMessage().getContent().trim().toLowerCase();
	}

	private static String normalizeResponse(String response, String prompt) throws DiscreteModeChoiceModel.NoFeasibleChoiceException {
		response = response.trim().toLowerCase();

		if (VALID_MODES.contains(response)) {
			return response;
		}

		for (String mode : VALID_MODES) {
			if (response.contains(mode)) {
				System.out.println("⚠️ Warning: Fuzzy match detected. Model returned: '" + response + "', interpreted as: '" + mode + "'");
				return mode;
			}
		}

		//throw new IllegalArgumentException("Invalid response from model: '" + response + "'. No valid mode could be detected.");
		System.out.println(prompt);
		throw new DiscreteModeChoiceModel.NoFeasibleChoiceException("Invalid response from model: '" + response + "'. No valid mode could be detected.");
	}

	private String getPersona(Person person) throws ToolInvocationException, OllamaBaseException, IOException, InterruptedException {
		String persona = person.getAttributes().getAttribute("persona").toString();
		if (persona==null){
			String systemPrompt = PromptBuilder.buildPersonaSystemPrompt();
			String personPrompt = PromptBuilder.buildPersonaPromptFromAttributes(person);
			persona = ask(systemPrompt, personPrompt);
			person.getAttributes().putAttribute("persona", persona);
		}
		return persona;
	}

}
