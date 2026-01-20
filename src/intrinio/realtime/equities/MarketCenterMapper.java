package intrinio.realtime.equities;

public class MarketCenterMapper
{
    public static MarketCenter map(char marketCenterCode, SubProvider subProvider)
    {
        switch (subProvider)
        {
            case CTA_A:
            case CTA_B:
                switch (marketCenterCode)
                {
                    case '2':
                        return MarketCenter.Undefined;
                    case 'A':
                    case 'a':
                        return MarketCenter.NyseAmerican;
                    case 'B':
                    case 'b':
                        return MarketCenter.NasdaqOmxBx;
                    case 'C':
                    case 'c':
                        return MarketCenter.NyseNational;
                    case 'D':
                    case 'd':
                        return MarketCenter.FinraAlternativeDisplayFacility;
                    case 'G':
                    case 'g':
                        return MarketCenter.Sip24XNationalExchange;
                    case 'H':
                    case 'h':
                        return MarketCenter.MiaxPearlExchange;
                    case 'I':
                    case 'i':
                        return MarketCenter.InternationalSecuritiesExchange;
                    case 'J':
                    case 'j':
                        return MarketCenter.CboeOneEdga;
                    case 'K':
                    case 'k':
                        return MarketCenter.CboeOneEdgx;
                    case 'L':
                    case 'l':
                        return MarketCenter.LongTermStockExchange;
                    case 'M':
                    case 'm':
                        return MarketCenter.NyseTexas;
                    case 'N':
                    case 'n':
                        return MarketCenter.Nyse;
                    case 'P':
                    case 'p':
                        return MarketCenter.NyseArca;
                    case 'Q':
                    case 'q':
                        return MarketCenter.Nasdaq;
                    case 'S':
                    case 's':
                        return MarketCenter.ConsolidatedTapeSystem;
                    case 'T':
                    case 't':
                        return MarketCenter.Nasdaq;
                    case 'U':
                    case 'u':
                        return MarketCenter.MembersExchange;
                    case 'V':
                    case 'v':
                        return MarketCenter.Iex;
                    case 'W':
                    case 'w':
                        return MarketCenter.Cboe;
                    case 'X':
                    case 'x':
                        return MarketCenter.NasdaqPsx;
                    case 'Y':
                    case 'y':
                        return MarketCenter.CboeOneByx;
                    case 'Z':
                    case 'z':
                        return MarketCenter.CboeOneBzx;
                    default:
                        return MarketCenter.Undefined;
                }
            case UTP:
                switch (marketCenterCode)
                {
                    case '2':
                        return MarketCenter.Undefined;
                    case 'A':
                    case 'a':
                        return MarketCenter.NyseAmerican;
                    case 'B':
                    case 'b':
                        return MarketCenter.NasdaqBx;
                    case 'C':
                    case 'c':
                        return MarketCenter.NyseNational;
                    case 'D':
                    case 'd':
                        return MarketCenter.FinancialIndustryRegulatoryAuthority;
                    case 'E':
                    case 'e':
                        return MarketCenter.UtpSystem;
                    case 'G':
                    case 'g':
                        return MarketCenter.Sip24XNationalExchange;
                    case 'H':
                    case 'h':
                        return MarketCenter.MiaxPearlExchange;
                    case 'I':
                    case 'i':
                        return MarketCenter.NasdaqIse;
                    case 'J':
                    case 'j':
                        return MarketCenter.CboeOneEdga;
                    case 'K':
                    case 'k':
                        return MarketCenter.CboeOneEdgx;
                    case 'L':
                    case 'l':
                        return MarketCenter.LongTermStockExchange;
                    case 'M':
                    case 'm':
                        return MarketCenter.NyseTexas;
                    case 'N':
                    case 'n':
                        return MarketCenter.Nyse;
                    case 'P':
                    case 'p':
                        return MarketCenter.NyseArca;
                    case 'Q':
                    case 'q':
                        return MarketCenter.Nasdaq;
                    case 'U':
                    case 'u':
                        return MarketCenter.MembersExchange;
                    case 'V':
                    case 'v':
                        return MarketCenter.Iex;
                    case 'W':
                    case 'w':
                        return MarketCenter.Cboe;
                    case 'X':
                    case 'x':
                        return MarketCenter.NasdaqPhiladelphia;
                    case 'Y':
                    case 'y':
                        return MarketCenter.CboeOneByx;
                    case 'Z':
                    case 'z':
                        return MarketCenter.CboeOneBzx;
                    default:
                        return MarketCenter.Undefined;
                }
            case OTC:
                switch (marketCenterCode)
                {
                    case 'O':
                        return MarketCenter.SipOtcOrf;
                    case ' ':
                        return MarketCenter.Undefined;
                    case 'e':
                        return MarketCenter.SipOtcExpert;
                    case 't':
                        return MarketCenter.SipOtcqx;
                    case 'b':
                        return MarketCenter.SipOtcqb;
                    case 'i':
                        return MarketCenter.SipOtcPink;
                    case 'G':
                        return MarketCenter.SipOtcGrey;
                    default:
                        return MarketCenter.Undefined;
                }
            case NASDAQ_BASIC:
                switch (marketCenterCode)
                {
                    case 'Q':
                    case 'q':
                        return MarketCenter.Nasdaq;
                    case 'L':
                    case 'l':
                        return MarketCenter.NasdaqTrfCarteret;
                    case '2':
                        return MarketCenter.NasdaqTrfChicago;
                    case 'B':
                    case 'b':
                        return MarketCenter.NasdaqBx;
                    case 'X':
                    case 'x':
                        return MarketCenter.NasdaqPsx;
                    case '!':
                    default:
                        return MarketCenter.Undefined;
                }
            case IEX:
                switch (marketCenterCode)
                {
                    case 'I':
                    case 'i':
                    case 'V':
                    case 'v':
                    default:
                        return MarketCenter.Iex;
                }
            case CBOE_ONE:
                switch (marketCenterCode)
                {
                    case '*': //All Cboe Markets for this feed
                        return MarketCenter.CboeOneAll;
                    case 'Y': //Cboe BYX Exchange (US only)
                    case 'y':
                        return MarketCenter.CboeOneByx;
                    case 'Z': //Cboe BZX Exchange (US only)
                    case 'z':
                        return MarketCenter.CboeOneBzx;
                    case 'A': //Cboe EDGA Exchange (US only)
                    case 'a':
                        return MarketCenter.CboeOneEdga;
                    case 'X': //Cboe EDGX Exchange (US only)
                    case 'x':
                        return MarketCenter.CboeOneEdgx;
                    case 'L': //NEO-L (Canada only)
                    case 'l':
                        return MarketCenter.CboeOneNeoL;
                    case 'N': //NEO-N (Canada only)
                    case 'n':
                        return MarketCenter.CboeOneNeoN;
                    case 't': //NEO-SST (Canada only)
                    case 'T':
                        return MarketCenter.CboeOneNeoSst;
                    case 'D': //NEO-D (Canada only)
                    case 'd':
                        return MarketCenter.CboeOneNeoD;
                    case 'M': //MATCHNow (Canada only)
                    case 'm':
                        return MarketCenter.CboeOneMatchNow;
                    case 'r': //NEO-Cross (Canada only)
                    case 'R':
                        return MarketCenter.CboeOneNeoCross;
                    case 'c':
                    case 'C':
                        return MarketCenter.CboeOneCtaAB;
                    case 'u':
                    case 'U':
                        return MarketCenter.CboeOneUtp;
                    default:
                        return MarketCenter.Undefined;
                }
            case EQUITIES_EDGE:
                return MarketCenter.Undefined;
            case NONE:
            default:
                return MarketCenter.Undefined;
        }
    }
}
