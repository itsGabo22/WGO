import { TelemetryData } from "@/hooks/useTelemetry";

export const MOCK_ROUTE_STEPS: TelemetryData[] = [
  {
    instruction: "Gira a la derecha en Calle 18",
    distanceToTurn: "200m",
    eta: "14 min",
    totalDistance: "3.2 km",
    turnDirection: "right",
  },
  {
    instruction: "Gira a la derecha en Calle 18",
    distanceToTurn: "150m",
    eta: "14 min",
    totalDistance: "3.1 km",
    turnDirection: "right",
  },
  {
    instruction: "Gira a la derecha en Calle 18",
    distanceToTurn: "50m",
    eta: "13 min",
    totalDistance: "3.0 km",
    turnDirection: "right",
  },
  {
    instruction: "Continúa por Carrera 27",
    distanceToTurn: "800m",
    eta: "13 min",
    totalDistance: "3.0 km",
    turnDirection: "straight",
  },
  {
    instruction: "Gira a la izquierda en Calle 20",
    distanceToTurn: "300m",
    eta: "10 min",
    totalDistance: "2.2 km",
    turnDirection: "left",
  },
  {
    instruction: "Llegando a tu destino",
    distanceToTurn: "0m",
    eta: "0 min",
    totalDistance: "0 km",
    turnDirection: "straight",
  },
];
