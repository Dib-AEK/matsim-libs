package org.matsim.contrib.common.zones.systems.grid.h3;

import com.google.common.base.Verify;
import org.matsim.contrib.common.zones.ZoneSystemParams;
import org.matsim.core.config.Config;

import jakarta.annotation.Nullable;


/**
 * @author nkuehnel / MOIA
 */
public class H3GridZoneSystemParams extends ZoneSystemParams {

	public static final String SET_NAME = "H3GridZoneSystem";

	public H3GridZoneSystemParams() {
		super(SET_NAME);
	}

	@Parameter
	@Comment("allows to configure H3 hexagonal zones. Used with zonesGeneration=H3. " +
		"Range from 0 (122 cells worldwide) to 15 (569 E^12 cells). " +
		"Usually meaningful between resolution 6 (3.7 km avg edge length) " +
		"and 10 (70 m avg edge length). ")
	@Nullable
	private Integer h3Resolution = null;

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		Verify.verify(getH3Resolution() != null && getH3Resolution() >= 0 && getH3Resolution() < 15, "H3 resolution must be a valid level between 0 and 15.");
	}

	@Nullable
	public Integer getH3Resolution() {
		return h3Resolution;
	}

	public void setH3Resolution(@Nullable Integer h3Resolution) {
		this.h3Resolution = h3Resolution;
	}
}
