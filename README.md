# ParkFlow – Automated Parking Reservation & Management System

**Group:** Y2S1-MLB-B10G1-07 | **Module:** SE2030 Software Engineering

Java web app (Servlets + MySQL) with **HTML/CSS/JS** UI (no JSP).

## Members / modules

| Module | Member | Pattern | Package |
|--------|--------|---------|---------|
| Reservation & Booking | Kumarathunga K.Y.L.M. | Observer | `reservation` |
| Live Slot Map | Rodrigo U.A.L. | MVC | `livemap` |
| Payment & Adaptive Rates | Gunathilaka M.V.N.S. | Strategy | `payment` |
| Loyalty Program | Jayasundera J.M.S.P. | Singleton | `loyalty` |
| Feedback & Rating | Dilukshitha A. | Factory | `feedback` |
| Customer Inquiries | Samarasinghe W.L.T.S. | State | `inquiry` |

## Setup

1. Install **JDK 11+**, **Maven**, **MySQL**, **Tomcat 9** (javax.servlet).
2. Create DB:
   ```bash
   mysql -u root -p < database/schema.sql
   mysql -u root -p < database/seed.sql
   ```
3. Edit `src/main/resources/db.properties` (user/password).
4. Build & deploy:
   ```bash
   mvn clean package
   ```
   Copy `target/parking-system.war` to Tomcat `webapps/`, or run from IDE with Tomcat.

5. Open `http://localhost:8080/parking-system/`

### Demo logins (password `Password@123`)

- `customer@park.lk` – Customer  
- `attendant@park.lk` – Gate attendant  
- `manager@park.lk` – Facility manager  
- `admin@park.lk` – Admin (manager UI)

## UI vs API

- Pages: `src/main/webapp/*.html` + `css/` + `js/`
- JSON APIs: `/api/auth`, `/api/map`, `/api/reservations`, `/api/payments`, `/api/loyalty`, `/api/feedback`, `/api/inquiries`, `/api/gate`
