package org.matsim.contrib.discrete_mode_choice.LLM;

import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import org.matsim.contribs.discrete_mode_choice.llm.LLM;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceModel;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class LLMTest {
	public static void main(String[] args) throws IOException, URISyntaxException, ToolInvocationException, OllamaBaseException, InterruptedException {
		String modelName = "gemma3:4b";
		float temperature = 1.0F;
		LLM llmModel = new LLM(modelName, temperature);

		System.out.println("LLM model is used for mode selection.");
		System.out.println("Model: " + modelName);
		System.out.println("Temperature: " + temperature);

		String systemMessage = "Your are an IA Agent, answer questions with only one word.";
		String prompt = "What is the capital of France ?";

		String response = llmModel.ask(systemMessage, prompt);

		System.out.println(response);
	}
}
