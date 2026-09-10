package utils.interfaces;

import exception.DataFileException;
import utils.enums.AdminIOCsvMode;

public interface DataExportable {
    void exportToCsv(String path, AdminIOCsvMode mode) throws DataFileException;
    void exportToTxt(String path, AdminIOCsvMode mode) throws DataFileException;
}
