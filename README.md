# README #

Класс CrptApi предназначен для работы с API Честного знака

### Описание методов ###
* CrptApi(TimeUnit timeUnit, int requestLimit) - конструктор класса, принимает на вход временной интервал и лимит количества запросов на этот интервал
* createCommissioningRFContract(Object document, String signature, String productGroup, String type, DocumentFormat format) - метод для подготовки тела запроса создания документа для ввода в оборот товара, произведённого в РФ
* sendPostRequest(List<NameValuePair> params, String method) - метод отправки http запроса, принимает на вход тело запроса в виде списка параметров и url метода
* getAuthorization() - метод для получения токена авторизации, проверяет наличие и актуальность текущего токена, в случае необходимости получает новый
* getAuthentication() - метод получения аутентификационной пары - uuid и data, проверяет наличие и актуальность текущей пары, при необходимости получает новую
* getSignedData() - метод для получения подписанных данных,  необходима имплементация


### Who do I talk to? ###

* svtr1995@mail.ru
* +79537449923