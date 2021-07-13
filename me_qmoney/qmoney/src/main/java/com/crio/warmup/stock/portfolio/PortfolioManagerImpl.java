package com.crio.warmup.stock.portfolio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {

  RestTemplate restTemplate;
  StockQuotesService stockQuotesService;

  // Caution: Do not delete or modify the constructor, or else your build will
  // break!
  // This is absolutely necessary for backward compatibility
  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }
  protected PortfolioManagerImpl(StockQuotesService stockQuotesService){
    this.stockQuotesService = stockQuotesService;
  }

  // TODO: CRIO_TASK_MODULE_REFACTOR
  // 1. Now we want to convert our code into a module, so we will not call it from
  // main anymore.
  // Copy your code from Module#3
  // PortfolioManagerApplication#calculateAnnualizedReturn
  // into #calculateAnnualizedReturn function here and ensure it follows the
  // method signature.
  // 2. Logic to read Json file and convert them into Objects will not be required
  // further as our
  // clients will take care of it, going forward.

  // Note:
  // Make sure to exercise the tests inside PortfolioManagerTest using command
  // below:
  // ./gradlew test --tests PortfolioManagerTest

  // CHECKSTYLE:OFF

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades, LocalDate endDate)
      throws StockQuoteServiceException
     {
    AnnualizedReturn annualizedReturn;
    List<AnnualizedReturn> annualizedReturnsList = new ArrayList<>();

    try{
    for (int i = 0; i < portfolioTrades.size(); i++) {
      annualizedReturn = getAnnualizedReturnObj(portfolioTrades.get(i), endDate);
      annualizedReturnsList.add(annualizedReturn);
    }
    Comparator<AnnualizedReturn> sortAnnualReturn = getComparator();
    Collections.sort(annualizedReturnsList, sortAnnualReturn);
  }
  catch(Exception e){
    throw new StockQuoteServiceException("Annualized return invalid response",e);
  }

    return annualizedReturnsList;
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(List<PortfolioTrade> portfolioTrades, LocalDate endDate,
		int numThreads) throws InterruptedException, StockQuoteServiceException {
      List<AnnualizedReturn> annualizedReturnsList = new ArrayList<>();
      List<Future<AnnualizedReturn>> futureReturnsList = new ArrayList<>();
      
      final ExecutorService pool = Executors.newFixedThreadPool(numThreads);

      for (int i = 0; i < portfolioTrades.size(); i++) {
        PortfolioTrade trade = portfolioTrades.get(i);
        Callable<AnnualizedReturn> callableTask = ()->{
          return getAnnualizedReturn(trade, endDate);
        };
        Future<AnnualizedReturn> futureReturn = pool.submit(callableTask);
        futureReturnsList.add(futureReturn);
      }
      pool.shutdown();
      pool.awaitTermination(200, TimeUnit.MILLISECONDS);
      
      for(int i = 0; i < portfolioTrades.size(); i++){
        Future<AnnualizedReturn> futureReturn = futureReturnsList.get(i);
        try{
          AnnualizedReturn annualizedReturn = futureReturn.get();
          annualizedReturnsList.add(annualizedReturn);
        }
        catch(RuntimeException e){
          throw new StockQuoteServiceException("Error when calling the API",e);
        }
        catch(Exception e){
          throw new StockQuoteServiceException("Error when calling the API",e);
        }
      }
      Comparator<AnnualizedReturn> comparable = getComparator();
      Collections.sort(annualizedReturnsList,comparable);
      

    return annualizedReturnsList;
  }

  public AnnualizedReturn getAnnualizedReturn(PortfolioTrade trade, LocalDate endDate) throws StockQuoteServiceException{
    AnnualizedReturn annualizedReturn;
    String symbol = trade.getSymbol();
    LocalDate startDate = trade.getPurchaseDate();
    try {
      List<Candle> candleList = getStockQuote(symbol, startDate, endDate);
      Double openAmt = candleList.get(0).getOpen();
      Double closeAmt = candleList.get(candleList.size() - 1).getClose();

      annualizedReturn = returnAnnualisedReturnsObj(endDate, trade, openAmt, closeAmt);
    } 
    catch (JsonProcessingException e) {
      return new AnnualizedReturn(trade.getSymbol(), Double.NaN, Double.NaN);
    }
    catch(RuntimeException e){
      // return new AnnualizedReturn(trade.getSymbol(),0.0,0.0);
      throw new StockQuoteServiceException("RuntimeException thrown while fetching Annualized Return");
    }
    catch(Exception e){
      return new AnnualizedReturn(trade.getSymbol(), Double.NaN, Double.NaN);
    }
    return annualizedReturn;
  }

  public AnnualizedReturn getAnnualizedReturnObj(PortfolioTrade trade, LocalDate endDate) throws JsonProcessingException, StockQuoteServiceException{
    AnnualizedReturn annualizedReturn;
    String symbol = trade.getSymbol();
    LocalDate startDate = trade.getPurchaseDate();
    try {
      List<Candle> candleList = getStockQuote(symbol, startDate, endDate);
      Double openAmt = candleList.get(0).getOpen();
      Double closeAmt = candleList.get(candleList.size() - 1).getClose();

      annualizedReturn = returnAnnualisedReturnsObj(endDate, trade, openAmt, closeAmt);
    } 
    catch (JsonProcessingException e) {
      return new AnnualizedReturn(trade.getSymbol(), Double.NaN, Double.NaN);
    }
    catch(Exception e){
      return new AnnualizedReturn(trade.getSymbol(), Double.NaN, Double.NaN);
    }
    return annualizedReturn;

  }

  public static AnnualizedReturn returnAnnualisedReturnsObj(LocalDate endDate, PortfolioTrade trade, Double buyPrice,
      Double sellPrice) {
    Double totalReturns = calculateTotalreturns(buyPrice, sellPrice);
    Double annualizedReturns = calcltAnnlzdRtrns(totalReturns, trade.getPurchaseDate(), endDate);
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturns, totalReturns);
  }

  public static Double calculateTotalreturns(Double buyPrice, Double sellPrice) {
    return ((sellPrice - buyPrice) / buyPrice);
  }

  public static Double calcltAnnlzdRtrns(Double totalReturns, LocalDate startdate, LocalDate endDate) {
    double days = ChronoUnit.DAYS.between(startdate, endDate);
    double totalNumOfYears = days / 365;
    return ((Math.pow((1 + totalReturns), (1 / totalNumOfYears))) - 1);
  }

  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  // CHECKSTYLE:OFF

  // TODO: CRIO_TASK_MODULE_REFACTOR
  // Extract the logic to call Tiingo third-party APIs to a separate function.
  // Remember to fill out the buildUri function and use that.

  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws JsonProcessingException, StockQuoteServiceException {
    // if (from.compareTo(to) >= 0) {
    //   throw new RuntimeException();
    // }
    // String url = buildUri(symbol, from, to);
    // List<Candle> candleObjArr = Arrays.asList(restTemplate.getForObject(url, TiingoCandle[].class));
    // if (candleObjArr == null) {
    //   return new ArrayList<Candle>();
    // } else
    //   return candleObjArr;

    return stockQuotesService.getStockQuote(symbol, from, to);

  }

  protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
    String uriTemplate = "https:api.tiingo.com/tiingo/daily/$SYMBOL/prices?"
        + "startDate=$STARTDATE&endDate=$ENDDATE&token=$APIKEY";
    String token = "f2d303e341b474c9ef9cf75b17ef6fac44a541af";
    String url = uriTemplate.replace("$SYMBOL", symbol).replace("$STARTDATE", startDate.toString())
        .replace("$ENDDATE", endDate.toString()).replace("$APIKEY", token);

    return url;
  }
  


  // ¶TODO: CRIO_TASK_MODULE_ADDITIONAL_REFACTOR
  //  Modify the function #getStockQuote and start delegating to calls to
  //  stockQuoteService provided via newly added constructor of the class.
  //  You also have a liberty to completely get rid of that function itself, however, make sure
  //  that you do not delete the #getStockQuote function.

}
