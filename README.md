PNML Sort
========

This application sorts PNML objects according to lexicographic order, in a simple text file.
The expected format is like in the following example:

    NET PhilosophersDyn-COL-03
      PAGE DocumentDefaultPage
        PLACES
			Forks
			HasLeft
			HasRight
			Neighbourhood
			Outside
			Think
			WaitLeft
			WaitRight
		TRANSITIONS
			Eat
			Initialize
			Join
			Leave
			SearchForks
			TakeLeft
			TakeRight
		ARCS
			Neighbourhood arc18 Eat
			Eat arc19 Neighbourhood
			TakeRight arc20 Neighbourhood
			Eat arc21 Forks
			Eat arc22 Think
			Neighbourhood arc23 TakeRight
			WaitRight arc24 TakeRight
			TakeRight arc25 HasRight
			HasRight arc26 Eat
			Forks arc27 TakeRight
			TakeLeft arc28 HasLeft
			HasLeft arc29 Eat
			WaitLeft arc30 TakeLeft
			Forks arc31 TakeLeft
			Think arc32 SearchForks
			Leave arc33 Neighbourhood
			SearchForks arc34 WaitRight
			SearchForks arc35 WaitLeft
			Forks arc36 Leave
			Think arc37 Leave
			Neighbourhood arc38 Leave
			Leave arc39 Outside
			Forks arc40 Join
			Join arc41 Think
			Initialize arc42 Forks
			Join arc43 Forks
			Outside arc44 Join
			Initialize arc45 Neighbourhood
			Join arc46 Neighbourhood
			Neighbourhood arc47 Join
			Outside arc48 Initialize
			Initialize arc49 Think
			Initialize arc50 Outside

The marking of places, and the inscription of arcs and the condition of transitions are missing
in the current version. Their support is planned in a upcoming release. I will update the format
accordingly.

