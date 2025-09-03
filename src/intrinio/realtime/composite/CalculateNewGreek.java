package intrinio.realtime.composite;

@FunctionalInterface
public interface CalculateNewGreek {
    void calculateNewGreek(OptionsContractData optionsContractData, SecurityData securityData, DataCache dataCache);
}
