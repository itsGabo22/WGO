"use client";

import { useState, useEffect } from "react";
import { MOCK_ROUTE_STEPS } from "@/constants/route";

export interface TelemetryData {
  instruction: string;
  distanceToTurn: string;
  eta: string;
  totalDistance: string;
  turnDirection: "left" | "right" | "straight";
}

export function useTelemetry(isActive: boolean) {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isFinished, setIsFinished] = useState(false);

  useEffect(() => {
    if (!isActive) return;

    const interval = setInterval(() => {
      setCurrentStepIndex((prev) => {
        if (prev < MOCK_ROUTE_STEPS.length - 1) {
          return prev + 1;
        } else {
          setIsFinished(true);
          return prev;
        }
      });
    }, 4000); // Progress to the next step every 4 seconds

    return () => clearInterval(interval);
  }, [isActive]);

  return {
    data: MOCK_ROUTE_STEPS[currentStepIndex],
    isFinished,
    progress: currentStepIndex / Math.max(1, MOCK_ROUTE_STEPS.length - 1)
  };
}
