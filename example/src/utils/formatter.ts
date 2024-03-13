let currencyFormatter: Intl.NumberFormat;
let langCurrency: string;

const generateCurrencyFormatter = (language: string) => {
  langCurrency = language;
  currencyFormatter = new Intl.NumberFormat(language, {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 2,
  });
  return currencyFormatter;
};

const language = "en";

export const getCurrentCurrencyFormatter = () =>
  currencyFormatter || generateCurrencyFormatter(language);

export const useCurrencyFormatter = () => {
  if (!currencyFormatter || langCurrency !== language) {
    currencyFormatter = generateCurrencyFormatter(language);
  }
  return currencyFormatter;
};
