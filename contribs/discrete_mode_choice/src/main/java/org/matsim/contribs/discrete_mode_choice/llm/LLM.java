package org.matsim.contribs.discrete_mode_choice.llm;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.utils.Options;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class LLM {

	private static final List<String> VALID_MODES = List.of("pt", "car", "bike", "walk");

	private final OllamaAPI ollamaAPI;
	private final String model;
	private final float temperature;

	public LLM(String model, float temperature) throws IOException, URISyntaxException {
		this(model, temperature, Utilities.getFromConfig("host"));
	}

	public LLM(String model, float temperature, String host) throws IOException, URISyntaxException {
		this.ollamaAPI = new OllamaAPI(host);
		this.ollamaAPI.setVerbose(false);
		this.model = model;
		this.temperature = temperature;
	}

	/**
	 * Core simulation query method. Returns one of: pt, car, bike, walk
	 */
	public String ask2(String tripMessage)
		throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException {
		String systemPrompt = buildSystemPrompt();
		OllamaChatRequest request = buildChatRequest(systemPrompt, tripMessage);
		String response = sendRequest(request);
		return normalizeResponse(response);
	}

	public String ask(Map<String, Object> agentAttributes, Map<String, Object> tripAttributes, List<String> modes)
		throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException {
		String systemPrompt = buildSystemPrompt();
		String tripMessage = "pick one of (pt, car, bike, walk)";
		OllamaChatRequest request = buildChatRequest(systemPrompt, tripMessage);
		String response = sendRequest(request);
		return normalizeResponse(response);
	}

	private static String buildSystemPrompt() {
		return String.format("""
            You are an AI agent used in a transportation network simulation for Switzerland.
            You represent a randomly generated Swiss commuter making realistic, stochastic decisions about transportation mode choice.

            For each trip scenario presented to you, you must choose **only one** of the following modes: `pt` (public transport), `car`, `bike`, or `walk`.

            Today's commuter profile: %s

            Your decision should reflect how this person might behave — considering factors such as:
            - Travel time for each mode
            - Monetary cost (e.g., fuel, tickets)
            - Availability of subscriptions or discounts
            - Parking availability and difficulty
            - Comfort and convenience
            - Weather conditions (if mentioned)
            - Personal habits or biases

            Do **not** output any explanation, justification, or extra text.
            Only return the selected mode as a single word from the allowed options.
            """, PersonsTypes.getPersonType());
	}

	private OllamaChatRequest buildChatRequest(String systemPrompt, String userPrompt) {
		Map<String, Object> optionsMap = new HashMap<>();
		optionsMap.put("temperature", this.temperature);
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

	private static String normalizeResponse(String response) {
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

		throw new IllegalArgumentException("Invalid response from model: '" + response + "'. No valid mode could be detected.");
	}

}
