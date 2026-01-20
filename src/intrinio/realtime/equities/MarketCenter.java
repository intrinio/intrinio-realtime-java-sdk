package intrinio.realtime.equities;

public enum MarketCenter {
    //Nasdaq
    Nasdaq,            //Q: The Nasdaq Stock Market
    NasdaqTrfCarteret, //L: Nasdaq/FINRA Trade Reporting Facility (TRF) Carteret
    NasdaqTrfChicago,  //2: Nasdaq/FINRA Trade Reporting Facility (TRF) Chicago
    NasdaqBx,          //B: Nasdaq BX (BX)
    NasdaqPsx,         //X: Nasdaq OMX PSX (PSX)
    NasdaqIse,
    NasdaqPhiladelphia,

    //IEX
    Iex, //Investors’ Exchange

    //SIP
    Nyse,
    NyseAmerican,
    NyseNational,
    NyseTexas,
    NyseArca,
    NasdaqOmxBx,
    FinraAlternativeDisplayFacility,
    Sip24XNationalExchange,
    MiaxPearlExchange,
    InternationalSecuritiesExchange,
    LongTermStockExchange,
    ConsolidatedTapeSystem,
    MembersExchange,
    FinancialIndustryRegulatoryAuthority,
    UtpSystem,

    //SIP OTC
    SipOtcOrf,    //ORF (OTC Equity Reporting Facility)
    SipOtcExpert, //Expert Market |
    SipOtcqx,     //OTCQX |
    SipOtcqb,     //OTCQB |
    SipOtcPink,   //Pink Market |
    SipOtcGrey,   //Grey Market |

    //CBOE
    Cboe,
    CboeOneAll,      //All Cboe Markets for this feed
    CboeOneByx,      //Cboe BYX Exchange (US only)
    CboeOneBzx,      //Cboe BZX Exchange (US only)
    CboeOneEdga,     //Cboe EDGA Exchange (US only)
    CboeOneEdgx,     //Cboe EDGX Exchange (US only)
    CboeOneNeoL,     //NEO-L (Canada only)
    CboeOneNeoN,     //NEO-N (Canada only)
    CboeOneNeoSst,   //NEO-SST (Canada only)
    CboeOneNeoD,     //NEO-D (Canada only)
    CboeOneMatchNow, //MATCHNow (Canada only)
    CboeOneNeoCross, //NEO-Cross (Canada only)
    CboeOneCtaAB,    //Consolidated Tape Association A/B
    CboeOneUtp,      //Unlisted Trading Privileges

    //Unknown
    Undefined
}
