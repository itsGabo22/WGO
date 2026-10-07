"use client";

import { useState, useEffect } from "react";

export interface TelemetryData {
  instruction: string;
  distanceToTurn: string;
  eta: string;
  totalDistance: string;
  turnDirection: "left" | "right" | "straight";
}

const mockRouteSteps: TelemetryData[] = [
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
];

export function useTelemetry(isActive: boolean) {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);

  useEffect(() => {
    if (!isActive) return;

    const interval = setInterval(() => {
      setCurrentStepIndex((prev) => {
        if (prev < mockRouteSteps.length - 1) {
          return prev + 1;
        }
        return prev;
      });
    }, 4000); // Progress to the next step every 4 seconds

    return () => clearInterval(interval);
  }, [isActive]);

  return mockRouteSteps[currentStepIndex];
}
