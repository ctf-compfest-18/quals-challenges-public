package utils.interfaces;

import exception.BurhanQuestException;
import utils.enums.AdminIOCsvMode;

public interface DataImportable {
    void importFromCsv(String path, AdminIOCsvMode mode) throws BurhanQuestException;
}
