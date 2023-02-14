# Dalsze plany na projekt

Celem projektu jest stworzenie UI podobnego do X2Go, więc w przyszłości proponujemy następujące zmiany:

Serwer powinien w pełni zarządzać sesjami VNC poprzez udostępnianie klientom następujących operacji (najlepiej zabezpieczonych dostępem przez SSH):

- utworzenie nowej sesji lub zwrócenie istniejącej
- połączenie się do sesji VNC
- zakończenie sesji

Ponadto serwer powinien obsługiwać inne środowiska graficzne.

Proponujemy, aby zrealizować go w formie [serwisu systemowego](https://wiki.archlinux.org/title/systemd), co będzie pozwało spełnić wymagania niektórych środowisk graficznych (np. uprawnienia administatora).

Rolą klienta powinno być jedynie komunikowanie się z serwerem (poprzez wyżej wymienione operacje) oraz nawiązywanie połączenia z sesją VNC. Proponujemy, aby klient nie mógł zdalnie wykonywać kodu na serwerze, tak jak to jest aktualnie zrealizowane.

## Strech goals: 

dalsze ułatwienie użytkownikowi procesu połączenia poprzez:
- automatyczne wykrywanie serwerów (usługa dostępna w oryginalnej aplikacji AVNC)
- w chwili łączenia serwer mógłby komunikować użytkownikowi aplikacji, jacy użytkownicy są do wyboru lub które środowiska graficzne są dostępne

