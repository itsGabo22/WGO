"use client";

import React from "react";
import { motion } from "framer-motion";
import { useTelemetry } from "@/hooks/useTelemetry";
import { NextTurnIndicator } from "./NextTurnIndicator";
import { AbstractMinimap } from "./AbstractMinimap";
import { BottomNavigationHUD } from "./BottomNavigationHUD";

interface ScreenActiveNavigationProps {
  isActive: boolean;
}

export function ScreenActiveNavigation({ isActive }: ScreenActiveNavigationProps) {
  const telemetry = useTelemetry(isActive);

  if (!telemetry) return null;

  return (
    <motion.div 
      className="flex h-full w-full flex-col items-center justify-between py-6 relative"
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.3 }}
    >
      <div className="w-full flex-1 flex flex-col items-center pt-2 gap-4">
        <NextTurnIndicator 
          instruction={telemetry.instruction}
          distance={telemetry.distanceToTurn}
          turnDirection={telemetry.turnDirection}
        />
        <AbstractMinimap />
      </div>

      <div className="w-full mb-2">
        <BottomNavigationHUD 
          eta={telemetry.eta}
          totalDistance={telemetry.totalDistance}
        />
      </div>
    </motion.div>
  );
}
