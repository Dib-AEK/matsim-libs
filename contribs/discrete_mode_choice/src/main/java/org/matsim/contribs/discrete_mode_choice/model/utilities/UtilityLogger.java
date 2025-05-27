package org.matsim.contribs.discrete_mode_choice.model.utilities;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UtilityLogger is responsible for logging utility candidates and their selection status
 * to a CSV file in a thread-safe manner.
 */
public class UtilityLogger {

	private static final Object LOCK = new Object();
	private static BufferedWriter writer = null;
	private static final AtomicInteger selectionCounter = new AtomicInteger(0);
	private static String csvFilePath = null;

	/**
	 * Initializes the logger by creating a BufferedWriter for the given file path.
	 *
	 * @param filePath the path to the output CSV file
	 */
	public static void init(String filePath) {
		if (filePath == null) {
			return;
		}

		synchronized (LOCK) {
			if (filePath.equals(csvFilePath)) {return;} // if it is the same file, do nothing.
			try {
				Path path = Path.of(filePath);
				writer = Files.newBufferedWriter(path);
				System.out.println("[DEBUG] UtilityLogger: Logger initialized for file: " + path.toAbsolutePath());
				writeHeader();
				csvFilePath = filePath;
			} catch (IOException e) {
				throw new RuntimeException("UtilityLogger: Failed to initialize CSV writer", e);
			}
		}
	}

	private static void writeHeader() throws IOException {
		writer.write("person_id;trips_index;selection_id;candidate_mode;utilities;utility;selected\n");
		writer.flush();
		System.out.println("[DEBUG] UtilityLogger: Head is written");
	}

	/**
	 * Logs a list of utility candidates, marking the selected candidate.
	 *
	 * @param candidates  the list of utility candidates
	 * @param isSelected  a function to determine whether a candidate is selected
	 */
	public static void logCandidates(Id<Person> personId, List<DiscreteModeChoiceTrip> tourTrips, List<UtilityCandidate> candidates, Function<UtilityCandidate, Boolean> isSelected) {
		if (writer == null) return;

		int selectionId = selectionCounter.incrementAndGet();

		synchronized (LOCK) {
			try {
				String index = tourTrips.stream()
										.map(t -> String.valueOf(t.getIndex()))
										.collect(Collectors.joining(","));

				for (UtilityCandidate candidate : candidates) {
					String line = String.format("%s;%s;%d;%s;%s;%.6f;%b\n",
						personId.toString(),
						// startTime,
						index,
						selectionId,
						candidate.getModes(),
						candidate.getUtilities(),
						candidate.getUtility(),
						isSelected.apply(candidate));
					writer.write(line);
				}
				writer.flush(); // Consider buffering writes for large volumes
			} catch (IOException e) {
				System.err.println("UtilityLogger: Failed to write to CSV: " + e.getMessage());
			}
		}
	}

	/**
	 * Closes the underlying writer, releasing any system resources.
	 */
	public static void close() {
		synchronized (LOCK) {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					System.err.println("Failed to close CSV writer: " + e.getMessage());
				}
			}
		}
	}
}
