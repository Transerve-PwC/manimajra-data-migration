package org.egov.manimajra.service;

import java.io.File;

import org.egov.manimajra.model.PropertyResponse;

public interface ReadExcelService {

	public PropertyResponse getDataFromExcel(File file, int sheetIndex);
	public PropertyResponse getDataFromExcelforOwner(File file, int sheetIndex);
	public PropertyResponse getDataFromExcelforPreviousOwner(File file, int sheetIndex);
	public PropertyResponse getDataFromExcelforCourtCase(File file, int sheetIndex);
	public PropertyResponse getDataFromExcelforDoc(File file, int sheetIndex);
}
