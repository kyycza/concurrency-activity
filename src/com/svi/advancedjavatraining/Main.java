package com.svi.advancedjavatraining;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.svi.advancedjavatraining.object.City;
import com.svi.advancedjavatraining.object.CityInfo;
import com.svi.advancedjavatraining.object.PopulationData;
import com.svi.advancedjavatraining.object.Province;
import com.svi.advancedjavatraining.utils.JSONFileReader;
import com.svi.advancedjavatraining.utils.PopulationFileWriter;
import com.svi.advancedjavatraining.utils.WebLoader;

public class Main {

	private static final int THREAD_COUNT = 64;

	public static void main(String[] args) throws Exception {
		long startTime = System.nanoTime();

		// TODO WORK AREA
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		try {
			Future<List<Province>> provinceFuture = executor.submit(() -> new WebLoader().getProvinces());
			Future<List<City>> cityFuture = executor.submit(() -> new WebLoader().getCities());
			Future<PopulationFileWriter> writerFuture = executor.submit(() -> new PopulationFileWriter());

			List<Province> provinces = provinceFuture.get();
			List<City> cities = cityFuture.get();

			Map<String, List<City>> citiesByProvinceKey = new HashMap<>();
			for (City city : cities) {
				citiesByProvinceKey.computeIfAbsent(city.getProvince(), key -> new ArrayList<>()).add(city);
			}

			List<Future<PopulationData>> rows = new ArrayList<>();
			for (Province province : provinces) {
				String provinceName = province.getName();
				for (City city : citiesByProvinceKey.getOrDefault(province.getKey(), Collections.emptyList())) {
					String cityName = city.getName();
					rows.add(executor.submit(() -> {
						CityInfo info = new JSONFileReader(provinceName, cityName).getData();
						double population = (info == null) ? 0 : info.getPopulation();
						return new PopulationData(provinceName, cityName, population);
					}));
				}
			}
			if (rows.size() != cities.size()) {
				System.out.println("Warning: " + (cities.size() - rows.size()) + " city(ies) have no matching province");
			}

			PopulationFileWriter writer = writerFuture.get();
			double totalPopulation = 0;
			for (Future<PopulationData> row : rows) {
				PopulationData data = row.get();
				writer.addPopulationRecord(data);
				totalPopulation += data.getPopulation();
			}

			new File("Output").mkdirs();
			writer.writeToFile();
			System.out.println("Total records: " + rows.size());
			System.out.println("Total population: " + String.format("%,.0f", totalPopulation));
		} finally {
			executor.shutdownNow();
		}

		// TODO END WORK AREA
		long endTime = System.nanoTime();
		long totalTime = endTime - startTime;
		long millis = TimeUnit.NANOSECONDS.toMillis(totalTime);

		long seconds = TimeUnit.NANOSECONDS.toSeconds(totalTime);

		long minutes = TimeUnit.NANOSECONDS.toMinutes(totalTime);

		long hours = TimeUnit.NANOSECONDS.toHours(totalTime);

		String time = String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
		System.out.println(time);
	}
}